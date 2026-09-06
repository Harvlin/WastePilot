package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.analytics.ReportSummaryResponse;
import com.project.wastepilot.domain.dto.analytics.ReportTopAction;
import com.project.wastepilot.domain.dto.analytics.ReportTopContributor;
import com.project.wastepilot.domain.dto.analytics.ReportTrendPoint;
import com.project.wastepilot.domain.dto.analytics.ReportsPayloadResponse;
import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.exception.ApiException;
import com.project.wastepilot.repository.ActivityLogRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.ReportsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportsServiceImpl implements ReportsService {

  private static final long OVERDUE_HOURS = 24L;
  private static final int TOP_ACTIONS_LIMIT = 5;
  private static final int TOP_CONTRIBUTORS_LIMIT = 5;

  private final ActivityLogRepository activityLogRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final WasteLogRepository wasteLogRepository;
  private final BatchRepository batchRepository;

  @Override
  @Transactional(readOnly = true)
  public ReportsPayloadResponse getReportsPayload(String period) {
    if (!"weekly".equalsIgnoreCase(period) && !"monthly".equalsIgnoreCase(period)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
          "Period must be 'weekly' or 'monthly'.");
    }

    boolean isWeekly = "weekly".equalsIgnoreCase(period);
    Instant now = Instant.now();
    ZonedDateTime nowUtc = now.atZone(ZoneOffset.UTC);

    List<TimeBucket> buckets = isWeekly
        ? buildDailyBuckets(nowUtc, 7)
        : buildMonthlyBuckets(nowUtc, 6);

    Instant windowStart = buckets.get(0).from();
    Instant windowEnd = buckets.get(buckets.size() - 1).to();

    List<ActivityLogEntity> activities = activityLogRepository.findByTimestampBetweenOrderByTimestampAsc(windowStart,
        windowEnd);
    List<InventoryLogEntity> inventoryLogs = inventoryLogRepository
        .findByTimestampBetweenOrderByTimestampAsc(windowStart, windowEnd);
    List<WasteLogEntity> wasteLogs = wasteLogRepository.findByTimestampBetweenOrderByTimestampAsc(windowStart,
        windowEnd);
    List<BatchEntity> startedBatches = batchRepository.findByStartedAtBetweenOrderByStartedAtAsc(windowStart,
        windowEnd);
    List<BatchEntity> closedBatches = batchRepository.findByClosedAtBetweenOrderByClosedAtAsc(windowStart, windowEnd);

    List<ReportTrendPoint> trend = buildTrend(buckets, inventoryLogs, wasteLogs, activities);

    ReportSummaryResponse summary = buildSummary(
        activities, inventoryLogs, wasteLogs, startedBatches, closedBatches, trend);

    List<ReportTopAction> topActions = buildTopActions(activities);

    List<ReportTopContributor> topContributors = buildTopContributors(activities);

    String windowLabel = buildWindowLabel(isWeekly, nowUtc);

    List<String> highlights = buildHighlights(summary, topActions);

    return new ReportsPayloadResponse(
        period.toLowerCase(),
        now.toString(),
        windowLabel,
        summary,
        trend,
        topActions,
        topContributors,
        highlights);
  }

  private List<TimeBucket> buildDailyBuckets(ZonedDateTime anchor, int days) {
    List<TimeBucket> buckets = new ArrayList<>();
    ZonedDateTime dayStart = anchor.truncatedTo(ChronoUnit.DAYS);
    for (int offset = days - 1; offset >= 0; offset--) {
      ZonedDateTime from = dayStart.minusDays(offset);
      ZonedDateTime to = from.plusDays(1);
      String label = from.getDayOfWeek()
          .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
      buckets.add(new TimeBucket(from.toInstant(), to.toInstant(), label));
    }
    return buckets;
  }

  private List<TimeBucket> buildMonthlyBuckets(ZonedDateTime anchor, int months) {
    List<TimeBucket> buckets = new ArrayList<>();
    ZonedDateTime monthStart = anchor.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    for (int offset = months - 1; offset >= 0; offset--) {
      ZonedDateTime from = monthStart.minusMonths(offset);
      ZonedDateTime to = from.plusMonths(1);
      String label = from.getMonth()
          .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
      buckets.add(new TimeBucket(from.toInstant(), to.toInstant(), label));
    }
    return buckets;
  }

  private List<ReportTrendPoint> buildTrend(
      List<TimeBucket> buckets,
      List<InventoryLogEntity> inventoryLogs,
      List<WasteLogEntity> wasteLogs,
      List<ActivityLogEntity> activities) {
    return buckets.stream().map(bucket -> {
      double inventoryIn = inventoryLogs.stream()
          .filter(i -> inBucket(i.getTimestamp(), bucket) && i.getType() == InventoryType.IN)
          .mapToDouble(i -> i.getQuantity().doubleValue())
          .sum();

      List<WasteLogEntity> bucketWaste = wasteLogs.stream()
          .filter(w -> inBucket(w.getTimestamp(), bucket))
          .toList();

      double wasteKg = bucketWaste.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
      double recoveredKg = bucketWaste.stream()
          .filter(w -> w.getDestination() == WasteDestination.reuse
              || w.getDestination() == WasteDestination.repair)
          .mapToDouble(w -> w.getQuantityKg().doubleValue())
          .sum();
      double landfillKg = bucketWaste.stream()
          .filter(w -> w.getDestination() == WasteDestination.dispose)
          .mapToDouble(w -> w.getQuantityKg().doubleValue())
          .sum();

      int transactions = (int) inventoryLogs.stream().filter(i -> inBucket(i.getTimestamp(), bucket)).count()
          + bucketWaste.size()
          + (int) activities.stream().filter(a -> inBucket(a.getTimestamp(), bucket)).count();

      BigDecimal circularScore = computeTrendScore(inventoryIn, wasteKg, recoveredKg, landfillKg);

      return new ReportTrendPoint(
          bucket.label(),
          circularScore,
          scale(wasteKg, 2),
          scale(recoveredKg, 2),
          scale(landfillKg, 2),
          transactions);
    }).toList();
  }

  private ReportSummaryResponse buildSummary(
      List<ActivityLogEntity> activities,
      List<InventoryLogEntity> inventoryLogs,
      List<WasteLogEntity> wasteLogs,
      List<BatchEntity> startedBatches,
      List<BatchEntity> closedBatches,
      List<ReportTrendPoint> trend) {
    long onTimeClosedCount = closedBatches.stream()
        .filter(b -> b.getClosedAt() != null
            && b.getStartedAt() != null
            && ChronoUnit.HOURS.between(b.getStartedAt(), b.getClosedAt()) <= OVERDUE_HOURS)
        .count();

    boolean onTimeEstimated = closedBatches.isEmpty();
    BigDecimal onTimeCloseRate = onTimeEstimated
        ? BigDecimal.valueOf(100)
        : scale((double) onTimeClosedCount / closedBatches.size() * 100, 1);

    double totalWasteKg = wasteLogs.stream().mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
    double recoveredWasteKg = wasteLogs.stream()
        .filter(w -> w.getDestination() == WasteDestination.reuse
            || w.getDestination() == WasteDestination.repair)
        .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();
    double landfillWasteKg = wasteLogs.stream()
        .filter(w -> w.getDestination() == WasteDestination.dispose)
        .mapToDouble(w -> w.getQuantityKg().doubleValue()).sum();

    double totalInvIn = inventoryLogs.stream()
        .filter(i -> i.getType() == InventoryType.IN)
        .mapToDouble(i -> i.getQuantity().doubleValue()).sum();
    double totalInvOut = inventoryLogs.stream()
        .filter(i -> i.getType() == InventoryType.OUT)
        .mapToDouble(i -> i.getQuantity().doubleValue()).sum();

    // circularScoreAvg: average of non-zero trend scores (real per-bucket data)
    List<Double> nonZeroScores = trend.stream()
        .map(t -> t.circularScore().doubleValue())
        .filter(s -> s > 0)
        .toList();
    BigDecimal circularScoreAvg = nonZeroScores.isEmpty()
        ? BigDecimal.ZERO
        : scale(nonZeroScores.stream().mapToDouble(d -> d).average().orElse(0), 1);

    return new ReportSummaryResponse(
        activities.size(),
        startedBatches.size(),
        closedBatches.size(),
        onTimeCloseRate,
        onTimeEstimated,
        scale(totalInvIn, 2),
        scale(totalInvOut, 2),
        scale(totalWasteKg, 2),
        scale(recoveredWasteKg, 2),
        scale(landfillWasteKg, 2),
        circularScoreAvg);
  }

  private List<ReportTopAction> buildTopActions(List<ActivityLogEntity> activities) {
    Map<String, Long> counts = activities.stream()
        .collect(Collectors.groupingBy(
            a -> a.getAction().replace("_", " "),
            Collectors.counting()));
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(TOP_ACTIONS_LIMIT)
        .map(e -> new ReportTopAction(e.getKey(), e.getValue().intValue()))
        .toList();
  }

  private List<ReportTopContributor> buildTopContributors(List<ActivityLogEntity> activities) {
    record ActorStats(int count, Instant lastSeen) {
    }

    Map<String, ActorStats> actorMap = activities.stream()
        .collect(Collectors.toMap(
            ActivityLogEntity::getActor,
            a -> new ActorStats(1, a.getTimestamp()),
            (existing, next) -> new ActorStats(
                existing.count() + 1,
                existing.lastSeen().isAfter(next.lastSeen()) ? existing.lastSeen() : next.lastSeen())));

    return actorMap.entrySet().stream()
        .sorted((e1, e2) -> Integer.compare(e2.getValue().count(), e1.getValue().count()))
        .limit(TOP_CONTRIBUTORS_LIMIT)
        .map(e -> new ReportTopContributor(e.getKey(), e.getValue().count(), e.getValue().lastSeen()))
        .toList();
  }

  private List<String> buildHighlights(ReportSummaryResponse summary, List<ReportTopAction> topActions) {
    List<String> highlights = new ArrayList<>();

    double totalWaste = summary.totalWasteKg().doubleValue();
    double recovered = summary.recoveredWasteKg().doubleValue();
    double landfill = summary.landfillWasteKg().doubleValue();

    double recoveredRate = totalWaste > 0 ? round(recovered / totalWaste * 100, 1) : 0;
    double landfillRate = totalWaste > 0 ? round(landfill / totalWaste * 100, 1) : 0;

    highlights.add(String.format(
        "Recovery reached %.1f%% with landfill share at %.1f%%.",
        recoveredRate, landfillRate));

    if (!topActions.isEmpty()) {
      ReportTopAction top = topActions.get(0);
      highlights.add(String.format(
          "Most frequent action: %s (%d logs).",
          top.action(), top.count()));
    } else {
      highlights.add("No dominant action detected in this period.");
    }

    double closeRate = summary.onTimeCloseRate().doubleValue();
    if (closeRate >= 85) {
      highlights.add(String.format(
          "Batch close discipline is healthy at %.1f%% on-time.", closeRate));
    } else {
      highlights.add(String.format(
          "Batch close discipline needs attention: %.1f%% on-time.", closeRate));
    }

    return highlights;
  }

  private String buildWindowLabel(boolean isWeekly, ZonedDateTime anchor) {
    if (isWeekly) {
      DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
      return "Last 7 days ending " + anchor.format(fmt);
    } else {
      DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
      return "Month to date (" + anchor.format(fmt) + ")";
    }
  }


  private BigDecimal computeTrendScore(double inputKg, double wasteKg, double recoveredKg, double landfillKg) {
    if (inputKg <= 0 && wasteKg <= 0)
      return BigDecimal.ZERO;
    double safeWaste = Math.max(0.0001, wasteKg);
    double safeInput = Math.max(1, inputKg);
    double recoveryRate = clamp(recoveredKg / safeWaste, 0, 1);
    double wasteEfficiency = clamp(1 - wasteKg / safeInput, 0, 1);
    double landfillShare = clamp(landfillKg / safeWaste, 0, 1);
    double landfillAvoidance = 1 - landfillShare;
    double base = 100 * (0.3 * recoveryRate + 0.25 * wasteEfficiency + 0.45 * landfillAvoidance);
    double cap = resolveLandfillCap(landfillShare);
    return scale(clamp(Math.min(base, cap), 0, 100), 1);
  }

  private double resolveLandfillCap(double share) {
    if (share > 0.4)
      return 55;
    if (share > 0.3)
      return 70;
    if (share > 0.2)
      return 80;
    return 100;
  }


  private record TimeBucket(Instant from, Instant to, String label) {
  }

  private boolean inBucket(Instant ts, TimeBucket bucket) {
    return ts != null && !ts.isBefore(bucket.from()) && ts.isBefore(bucket.to());
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private double round(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
  }

  private BigDecimal scale(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
  }
}
