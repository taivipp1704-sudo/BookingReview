package com.claritycam.platform.dto.catalog;

import com.claritycam.platform.model.catalog.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductCreatePayload(
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{2,31}") String productCode,
    @NotNull @Valid ProductPayload product,
    @Min(0) @Max(100_000) Integer initialStockQty,
    @Size(max = 200) List<@NotBlank @Size(max = 96) String> serialNumbers) {}
