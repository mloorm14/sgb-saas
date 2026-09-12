package com.uteq.backend.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public interface ReportOverduesProjection {

    Long getLoanId();

    String getUserName();

    String getUserEmail();

    String getBookTitle();

    String getBookIsbn();

    Instant getDateLoanReturnEstimada();

    Long getDaysAtraso();

    BigDecimal getAmountFineEstimada();
}
