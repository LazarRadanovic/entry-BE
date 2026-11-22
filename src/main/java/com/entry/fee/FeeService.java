package com.entry.fee;

import com.entry.common.exception.ApiException;
import com.entry.entrance.Entrance;
import com.entry.entrance.EntranceRepository;
import com.entry.fee.dto.FeeRowDto;
import com.entry.fee.dto.FeeTableDto;
import com.entry.security.AuthUser;
import com.entry.user.User;
import com.entry.user.UserRepository;
import com.entry.user.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
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

/** Monthly building-cleaning fee: one row per apartment, admin marks who paid. */
@Service
@RequiredArgsConstructor
public class FeeService {

    private final FeeUnitRepository units;
    private final FeePaymentRepository payments;
    private final UserRepository users;
    private final EntranceRepository entrances;
    private final ApplicationEventPublisher events;

    /** Everyone in the entrance can see the table. Residents' apartments are added to it automatically. */
    @Transactional
    public FeeTableDto table(AuthUser me, Integer requestedYear) {
        int year = requestedYear == null ? Year.now().getValue() : requestedYear;
        if (year < 2000 || year > 2100) {
            throw ApiException.badRequest("INVALID_YEAR", "Invalid year");
        }
        Entrance entrance = entrance(me);
        List<User> members = users.findByEntrance_IdAndStatusOrderByCreatedAtAsc(me.entranceId(), UserStatus.ACTIVE);
        List<FeeUnit> all = syncUnits(me.entranceId(), members);

        Map<String, List<User>> residentsByKey = members.stream()
                .collect(Collectors.groupingBy(u -> FeeUnit.key(u.getFloor(), u.getApartment()), LinkedHashMap::new,
                        Collectors.toList()));
        String myKey = members.stream().filter(u -> u.getId().equals(me.id())).findFirst()
                .map(u -> FeeUnit.key(u.getFloor(), u.getApartment())).orElse(null);

        Map<UUID, Instant[]> paid = new HashMap<>();
        if (!all.isEmpty()) {
            for (FeePaymentRepository.PaymentRow row : payments.findForYear(year,
                    all.stream().map(FeeUnit::getId).toList())) {
                paid.computeIfAbsent(row.getUnitId(), k -> new Instant[12])[row.getMonth() - 1] = row.getPaidAt();
            }
        }

        List<FeeRowDto> rows = all.stream()
                .sorted(Comparator.comparing(FeeUnit::getFloor, FeeService::natural)
                        .thenComparing(FeeUnit::getApartment, FeeService::natural))
                .map(u -> new FeeRowDto(
                        u.getId(),
                        u.getFloor(),
                        u.getApartment(),
                        residentsByKey.getOrDefault(u.key(), List.of()).stream()
                                .map(r -> r.getFirstName() + " " + r.getLastName()).toList(),
                        u.key().equals(myKey),
                        Arrays.asList(paid.getOrDefault(u.getId(), new Instant[12]))))
                .toList();
        return new FeeTableDto(year, entrance.getFeeAmount(), entrance.getFeeCurrency(), rows);
    }

    @Transactional
    public void setPaid(AuthUser admin, UUID unitId, int year, int month, boolean paid) {
        FeeUnit unit = unit(admin, unitId);
        if (paid) {
            payments.markPaid(unit.getId(), year, month, entrance(admin).getFeeAmount(), admin.id());
        } else {
            payments.markUnpaid(unit.getId(), year, month);
        }
        events.publishEvent(new FeesChangedEvent(admin.entranceId(), year));
    }

    @Transactional
    public void updateSettings(AuthUser admin, BigDecimal amount, String currency) {
        Entrance e = entrance(admin);
        e.setFeeAmount(amount);
        e.setFeeCurrency(currency);
        events.publishEvent(new FeesChangedEvent(admin.entranceId(), Year.now().getValue()));
    }

    /** For apartments without app users (e.g. elderly neighbours who don't use the app). */
    @Transactional
    public void addUnit(AuthUser admin, String floor, String apartment) {
        String key = FeeUnit.key(floor, apartment);
        boolean exists = units.findByEntranceId(admin.entranceId()).stream().anyMatch(u -> u.key().equals(key));
        if (exists) {
            throw ApiException.conflict("UNIT_EXISTS", "This apartment is already in the table");
        }
        units.save(new FeeUnit(admin.entranceId(), floor, apartment));
        events.publishEvent(new FeesChangedEvent(admin.entranceId(), Year.now().getValue()));
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
        units.delete(unit);
        events.publishEvent(new FeesChangedEvent(admin.entranceId(), Year.now().getValue()));
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

    private FeeUnit unit(AuthUser me, UUID unitId) {
        return units.findByIdAndEntranceId(unitId, me.entranceId())
                .orElseThrow(() -> ApiException.notFound("Apartment not found"));
    }

    private Entrance entrance(AuthUser me) {
        return entrances.findById(me.entranceId()).orElseThrow(() -> ApiException.notFound("Entrance not found"));
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
