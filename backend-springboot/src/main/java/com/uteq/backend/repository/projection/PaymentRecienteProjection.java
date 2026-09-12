package com.uteq.backend.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public interface PaymentRecienteProjection {
    Long getFineId();
    BigDecimal getAmountPaid();
    Instant getDatePaid();
    String getUserEmail();
    String getUserName();
    String getBookTitle();
}
