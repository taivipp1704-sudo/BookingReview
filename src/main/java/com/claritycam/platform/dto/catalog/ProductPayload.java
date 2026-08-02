package com.claritycam.platform.dto.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductPayload(
    @NotBlank @Size(max = 32) String levelCode,
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 80) String brand,
    @NotBlank @Size(max = 80) String category,
    @DecimalMin(value = "0.00") BigDecimal hourlyPrice,
    @DecimalMin(value = "0.00") BigDecimal halfDayPrice,
    @NotNull @DecimalMin(value = "0.00") BigDecimal dailyPrice,
    @DecimalMin(value = "0.00") BigDecimal twoDayPrice,
    @DecimalMin(value = "0.00") BigDecimal multiDayPrice,
    @Min(2) @Max(365) Integer multiDayDays,
    @DecimalMin(value = "0.00") BigDecimal extraDayPrice,
    @DecimalMin(value = "0.00") BigDecimal equipmentDeposit,
    @DecimalMin(value = "0.00") BigDecimal bookingDeposit,
    @DecimalMin(value = "0.00") BigDecimal lateFeePerHour,
    @DecimalMin(value = "0.00") BigDecimal identityViolationFee,
    @DecimalMin(value = "0.00") BigDecimal unauthorizedTransferFee,
    @DecimalMin(value = "0.00") @jakarta.validation.constraints.DecimalMax(value = "100.00") BigDecimal impactPenaltyPercent,
    @DecimalMin(value = "0.00") BigDecimal damageLiabilityLimit,
    boolean included,
    boolean active,
    @Size(max = 1000) String imageUrl,
    @NotBlank @Size(max = 500) String specs,
    @NotBlank @Pattern(regexp = "SERIALIZED|BATCH_TRACKED|QUANTITY|CONSUMABLE|BULK") String trackingMode,
    @Size(max = 48) String serialPrefix,
    @Size(max = 64) String storeBranchId,
    @Min(0) @Max(1_000_000_000) long bookingCountBase,
    @Size(max = 4000) String customAttributes) {}
