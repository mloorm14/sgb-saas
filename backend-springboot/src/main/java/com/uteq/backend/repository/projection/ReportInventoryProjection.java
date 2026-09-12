package com.uteq.backend.repository.projection;

public interface ReportInventoryProjection {

    Long getBookId();

    String getTitle();

    String getIsbn();

    String getAuthorName();

    String getCategoryName();

    String getPublisherName();

    String getSupplierName();

    String getLanguageName();

    String getStatusBookName();

    Short getYearPublication();

    String getLocationPhysical();

    Short getStockTotal();

    Short getStockAvailable();

    String getStatusAvailability();
}
