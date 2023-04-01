package com.sys4life.inventorytmg.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StorageStructure {
    private List<Storage> negatives;
    private List<Storage> zeros;
    private List<Storage> positives;
    private List<Storage> positiveLimitOne;
    private List<Storage> positiveLimitTwo;
    private List<Storage> positiveLimitThree;
    private List<Storage> restrictives;
}
