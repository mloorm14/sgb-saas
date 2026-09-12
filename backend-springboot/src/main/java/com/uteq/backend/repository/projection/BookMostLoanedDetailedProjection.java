package com.uteq.backend.repository.projection;

import java.math.BigDecimal;

public interface BookMostLoanedDetailedProjection {

    Long getBookId();

    String getTitle();

    String getIsbn();

    String getAuthorName();

    String getCategoryName();

    Long getTotalLoans();

    BigDecimal getPercentage();
}
