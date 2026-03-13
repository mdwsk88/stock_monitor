package com.dawei.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Aggregated US stock alert row with frequency from SQL ranking query.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class USStockRssAggregate extends USStockRss {

    private int frequency;
}
