package com.uteq.backend.repository.projection;

import java.math.BigDecimal;

public interface ReportCategoriesDemandedProjection {

    Integer getCategoryId();

    String getCategoryName();

    Long getTotalLoans();

    BigDecimal getPercentage();
}
