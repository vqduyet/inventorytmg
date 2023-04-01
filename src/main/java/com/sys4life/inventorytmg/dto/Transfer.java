package com.sys4life.inventorytmg.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Transfer {
    private String productCode;
    private String inStorage;
    private int inQuantity;
    private String outStorage;
    private int outQuantity;
}
