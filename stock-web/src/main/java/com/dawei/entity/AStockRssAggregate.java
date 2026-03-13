package com.dawei.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Aggregated A-share alert row with frequency from SQL ranking query.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AStockRssAggregate extends AStockRss {

    private int frequency;
}
