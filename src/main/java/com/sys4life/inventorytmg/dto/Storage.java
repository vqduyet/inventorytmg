package com.sys4life.inventorytmg.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Storage {
    private String name;
    private int quantity;
}
