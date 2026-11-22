package com.entry.fee;

import com.entry.common.exception.ApiException;
import com.entry.fee.dto.CostDto;
import com.entry.fee.dto.CostRequest;
import com.entry.fee.dto.CostRowDto;
import com.entry.fee.dto.CostTableDto;
import com.entry.fee.dto.MyCostDto;
import com.entry.fee.dto.MyCostsDto;
import com.entry.fee.dto.UnitDto;
import com.entry.security.AuthUser;
import com.entry.user.User;
import com.entry.user.UserRepository;
import com.entry.user.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrance costs: the admin defines costs (building cleaning, garage cleaning, repairs...), assigns each one to
 * all apartments or a subset, and marks who paid. Everyone in the entrance can see the tables.
 */
@Service
@RequiredArgsConstructor
public class CostService {

    private final FeeUnitRepository units;
    private final CostRepository costs;
    private final CostPaymentRepository payments;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    // ---------- reading ----------

    /** All apartments of the entrance (residents' apartments are added automatically). */
    @Transactional
    public List<UnitDto> units(AuthUser me) {
        Context ctx = context(me);
        return ctx.units.stream()
                .map(u -> new UnitDto(u.getId(), u.getFloor(), u.getApartment(), u.getAreaM2(), ctx.residents(u),
                        ctx.isMine(u)))
                .toList();
    }

    @Transactional
    public List<CostDto> costs(AuthUser me) {
        Context ctx = context(me);
        return costs.findByEntranceIdAndActiveTrueOrderByCreatedAtAsc(me.entranceId()).stream()
                .map(c -> toDto(c, ctx.assigned(c)))
                .toList();
    }

    @Transactional
    public CostTableDto table(AuthUser me, UUID costId, Integer requestedYear) {
        int year = year(requestedYear);
        Context ctx = context(me);
        Cost cost = cost(me, costId);
        List<FeeUnit> assigned = ctx.assigned(cost);
        Map<UUID, BigDecimal> shares = CostShares.compute(cost.getType(), cost.getAmount(), assigned);
        Map<UUID, Map<UUID, Instant[]>> paid = paid(List.of(cost), year);

        List<CostRowDto> rows = assigned.stream()
                .map(u -> new CostRowDto(u.getId(), u.getFloor(), u.getApartment(), u.getAreaM2(), ctx.residents(u),
                        ctx.isMine(u), shares == null ? null : shares.get(u.getId()),
                        paidList(cost, paid, u.getId())))
                .toList();
        return new CostTableDto(year, toDto(cost, shares == null), rows);
    }

    /** Costs that apply to the caller's apartment. */
    @Transactional
    public MyCostsDto mine(AuthUser me, Integer requestedYear) {
        int year = year(requestedYear);
        Context ctx = context(me);
        FeeUnit myUnit = ctx.units.stream().filter(ctx::isMine).findFirst().orElse(null);
        if (myUnit == null) {
            return new MyCostsDto(year, null, null, null, List.of());
        }
        List<Cost> mineCosts = costs.findByEntranceIdAndActiveTrueOrderByCreatedAtAsc(me.entranceId()).stream()
                .filter(c -> c.appliesTo(myUnit.getId()))
                .toList();
        Map<UUID, Map<UUID, Instant[]>> paid = paid(mineCosts, year);
        List<MyCostDto> items = mineCosts.stream().map(c -> {
            Map<UUID, BigDecimal> shares = CostShares.compute(c.getType(), c.getAmount(), ctx.assigned(c));
            return new MyCostDto(toDto(c, shares == null), shares == null ? null : shares.get(myUnit.getId()),
                    paidList(c, paid, myUnit.getId()));
        }).toList();
        return new MyCostsDto(year, myUnit.getId(), myUnit.getFloor(), myUnit.getApartment(), items);
    }

    // ---------- admin: costs ----------

    @Transactional
    public CostDto create(AuthUser admin, CostRequest r) {
        Cost cost = new Cost();
        cost.setEntranceId(admin.entranceId());
        apply(admin, cost, r);
        costs.save(cost);
        changed(admin);
        return toDto(cost, context(admin).assigned(cost));
    }

    @Transactional
    public CostDto update(AuthUser admin, UUID costId, CostRequest r) {
        Cost cost = cost(admin, costId);
        apply(admin, cost, r);
        changed(admin);
        return toDto(cost, context(admin).assigned(cost));
    }

    @Transactional
    public CostDto assign(AuthUser admin, UUID costId, boolean appliesToAll, List<UUID> unitIds) {
        Cost cost = cost(admin, costId);
        assignUnits(admin, cost, cost.getType(), appliesToAll, unitIds);
        changed(admin);
        return toDto(cost, context(admin).assigned(cost));
    }

    /** Costs with recorded payments are archived (hidden) so the history stays; others are deleted. */
    @Transactional
    public void delete(AuthUser admin, UUID costId) {
        Cost cost = cost(admin, costId);
        if (payments.anyFor(cost.getId())) {
            cost.setActive(false);
        } else {
            costs.delete(cost);
        }
        changed(admin);
    }

    @Transactional
    public void setPaid(AuthUser admin, UUID costId, UUID unitId, String period, boolean paid) {
        Cost cost = cost(admin, costId);
        boolean once = "ONCE".equals(period);
        if (once != (cost.getRecurrence() == CostRecurrence.ONE_TIME)) {
            throw ApiException.badRequest("INVALID_PERIOD",
                    once ? "This cost is monthly: use YYYY-MM" : "This is a one-time cost: use ONCE");
        }
        FeeUnit unit = unit(admin, unitId);
        if (!cost.appliesTo(unit.getId())) {
            throw ApiException.badRequest("UNIT_NOT_ASSIGNED", "This cost doesn't apply to that apartment");
        }
        if (paid) {
            Map<UUID, BigDecimal> shares = CostShares.compute(cost.getType(), cost.getAmount(),
                    context(admin).assigned(cost));
            payments.markPaid(cost.getId(), unit.getId(), period, shares == null ? null : shares.get(unit.getId()),
                    admin.id());
        } else {
            payments.markUnpaid(cost.getId(), unit.getId(), period);
        }
        changed(admin);
    }

    // ---------- admin: apartments ----------

    /** For apartments without app users (e.g. elderly neighbours who don't use the app). */
    @Transactional
    public void addUnit(AuthUser admin, String floor, String apartment, BigDecimal areaM2) {
        String key = FeeUnit.key(floor, apartment);
        boolean exists = units.findByEntranceId(admin.entranceId()).stream().anyMatch(u -> u.key().equals(key));
        if (exists) {
            throw ApiException.conflict("UNIT_EXISTS", "This apartment is already in the table");
        }
        FeeUnit unit = new FeeUnit(admin.entranceId(), floor, apartment);
        unit.setAreaM2(areaM2);
        units.save(unit);
        changed(admin);
    }

    @Transactional
    public void updateUnit(AuthUser admin, UUID unitId, BigDecimal areaM2) {
        unit(admin, unitId).setAreaM2(areaM2);
        changed(admin);
    }

    /** Only apartments nobody lives in (according to the app) can be removed; their history goes too. */
    @Transactional
    public void removeUnit(AuthUser admin, UUID unitId) {
        FeeUnit unit = unit(admin, unitId);
        boolean occupied = users.findByEntrance_IdAndStatusOrderByCreatedAtAsc(admin.entranceId(), UserStatus.ACTIVE)
                .stream().anyMatch(u -> FeeUnit.key(u.getFloor(), u.getApartment()).equals(unit.key()));
        if (occupied) {
            throw ApiException.conflict("UNIT_OCCUPIED", "A resident lives in this apartment, so it can't be removed");
        }
        costs.findByEntranceIdAndActiveTrueOrderByCreatedAtAsc(admin.entranceId())
                .forEach(c -> c.getUnitIds().remove(unit.getId()));
        units.delete(unit);
        changed(admin);
    }

    // ---------- helpers ----------

    private void apply(AuthUser admin, Cost cost, CostRequest r) {
        cost.setName(r.name().trim());
        cost.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        cost.setType(r.type());
        cost.setAmount(r.amount());
        cost.setCurrency(r.currency());
        cost.setRecurrence(r.recurrence());
        cost.setDueDate(r.recurrence() == CostRecurrence.ONE_TIME ? r.dueDate() : null);
        assignUnits(admin, cost, r.type(), r.appliesToAll(), r.unitIds());
    }

    private void assignUnits(AuthUser admin, Cost cost, CostType type, boolean appliesToAll, List<UUID> unitIds) {
        Set<UUID> ids = appliesToAll || unitIds == null ? Set.of() : new HashSet<>(unitIds);
        if (!appliesToAll) {
            if (ids.isEmpty()) {
                throw ApiException.badRequest("NO_UNITS", "Choose at least one apartment");
            }
            Set<UUID> own = units.findByEntranceId(admin.entranceId()).stream().map(FeeUnit::getId)
                    .collect(Collectors.toSet());
            if (!own.containsAll(ids)) {
                throw ApiException.badRequest("INVALID_UNIT", "Unknown apartment");
            }
        }
        cost.setAppliesToAll(appliesToAll);
        cost.getUnitIds().clear();
        cost.getUnitIds().addAll(ids);
    }

    /** costId -> unitId -> paid-at per slot (12 months, or 1 for one-time costs). */
    private Map<UUID, Map<UUID, Instant[]>> paid(Collection<Cost> list, int year) {
        Map<UUID, Map<UUID, Instant[]>> result = new HashMap<>();
        if (list.isEmpty()) {
            return result;
        }
        Map<UUID, Cost> byId = list.stream().collect(Collectors.toMap(Cost::getId, Function.identity()));
        for (CostPaymentRepository.PaymentRow row : payments.findForYear(byId.keySet(), year + "-%")) {
            Cost c = byId.get(row.getCostId());
            boolean once = c.getRecurrence() == CostRecurrence.ONE_TIME;
            if (once != "ONCE".equals(row.getPeriod())) {
                continue; // recurrence was changed after payments were recorded
            }
            int slot = once ? 0 : Integer.parseInt(row.getPeriod().substring(5)) - 1;
            result.computeIfAbsent(c.getId(), k -> new HashMap<>())
                    .computeIfAbsent(row.getUnitId(), k -> new Instant[once ? 1 : 12])[slot] = row.getPaidAt();
        }
        return result;
    }

    private static List<Instant> paidList(Cost c, Map<UUID, Map<UUID, Instant[]>> paid, UUID unitId) {
        int slots = c.getRecurrence() == CostRecurrence.ONE_TIME ? 1 : 12;
        Instant[] values = paid.getOrDefault(c.getId(), Map.of()).getOrDefault(unitId, new Instant[slots]);
        return Arrays.asList(values);
    }

    private static CostDto toDto(Cost c, List<FeeUnit> assigned) {
        return toDto(c, CostShares.compute(c.getType(), c.getAmount(), assigned) == null);
    }

    private static CostDto toDto(Cost c, boolean missingArea) {
        List<UUID> ids = c.isAppliesToAll() ? List.of() : c.getUnitIds().stream().sorted().toList();
        return new CostDto(c.getId(), c.getName(), c.getDescription(), c.getType(), c.getAmount(), c.getCurrency(),
                c.getRecurrence(), c.isAppliesToAll(), ids, c.getDueDate(), missingArea);
    }

    private void changed(AuthUser admin) {
        events.publishEvent(new CostsChangedEvent(admin.entranceId()));
    }

    private Cost cost(AuthUser me, UUID costId) {
        return costs.findByIdAndEntranceIdAndActiveTrue(costId, me.entranceId())
                .orElseThrow(() -> ApiException.notFound("Cost not found"));
    }

    private FeeUnit unit(AuthUser me, UUID unitId) {
        return units.findByIdAndEntranceId(unitId, me.entranceId())
                .orElseThrow(() -> ApiException.notFound("Apartment not found"));
    }

    private static int year(Integer requested) {
        int year = requested == null ? Year.now().getValue() : requested;
        if (year < 2000 || year > 2100) {
            throw ApiException.badRequest("INVALID_YEAR", "Invalid year");
        }
        return year;
    }

    /** Sorted apartments of the entrance, who lives where, and which one is the caller's. */
    private Context context(AuthUser me) {
        List<User> members = users.findByEntrance_IdAndStatusOrderByCreatedAtAsc(me.entranceId(), UserStatus.ACTIVE);
        List<FeeUnit> all = syncUnits(me.entranceId(), members).stream()
                .sorted(Comparator.comparing(FeeUnit::getFloor, CostService::natural)
                        .thenComparing(FeeUnit::getApartment, CostService::natural))
                .toList();
        Map<String, List<String>> residents = members.stream()
                .collect(Collectors.groupingBy(u -> FeeUnit.key(u.getFloor(), u.getApartment()), LinkedHashMap::new,
                        Collectors.mapping(u -> u.getFirstName() + " " + u.getLastName(), Collectors.toList())));
        String myKey = members.stream().filter(u -> u.getId().equals(me.id())).findFirst()
                .map(u -> FeeUnit.key(u.getFloor(), u.getApartment())).orElse(null);
        return new Context(all, residents, myKey);
    }

    private record Context(List<FeeUnit> units, Map<String, List<String>> residentsByKey, String myKey) {

        List<String> residents(FeeUnit u) {
            return residentsByKey.getOrDefault(u.key(), List.of());
        }

        boolean isMine(FeeUnit u) {
            return u.key().equals(myKey);
        }

        List<FeeUnit> assigned(Cost c) {
            return units.stream().filter(u -> c.appliesTo(u.getId())).toList();
        }
    }

    /** Makes sure every resident's apartment has a row. */
    private List<FeeUnit> syncUnits(UUID entranceId, List<User> members) {
        List<FeeUnit> existing = new ArrayList<>(units.findByEntranceId(entranceId));
        Set<String> known = existing.stream().map(FeeUnit::key).collect(Collectors.toSet());
        Map<String, User> missing = members.stream()
                .filter(u -> !known.contains(FeeUnit.key(u.getFloor(), u.getApartment())))
                .collect(Collectors.toMap(u -> FeeUnit.key(u.getFloor(), u.getApartment()), Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
        missing.values().forEach(u -> existing.add(units.save(new FeeUnit(entranceId, u.getFloor(), u.getApartment()))));
        return existing;
    }

    /** "2" < "10" < "P" (numbers first, numerically; then text, case-insensitive). */
    static int natural(String a, String b) {
        Integer na = parse(a);
        Integer nb = parse(b);
        if (na != null && nb != null) return Integer.compare(na, nb);
        if (na != null) return -1;
        if (nb != null) return 1;
        return a.compareToIgnoreCase(b);
    }

    private static Integer parse(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
