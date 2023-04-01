package com.sys4life.inventorytmg.dto;

public class Issue {
    String productCode;
    String issue;

    public Issue() {
    }

    public Issue(String productCode, String issue) {
        this.productCode = productCode;
        this.issue = issue;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }
}
