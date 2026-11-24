package com.marketplace.backend.service.risk;

import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.RetentionAction;
import com.marketplace.backend.entity.SavedVoucher;
import com.marketplace.backend.entity.Voucher;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.RetentionActionRepository;
import com.marketplace.backend.repository.SavedVoucherRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.repository.VoucherRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.AdminAuditService;
import com.marketplace.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChurnServiceTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final VoucherRepository vouchers = mock(VoucherRepository.class);
    private final SavedVoucherRepository saved = mock(SavedVoucherRepository.class);
    private final RetentionActionRepository actions = mock(RetentionActionRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ChurnService service = new ChurnService(mock(RiskModelClient.class), jdbc, vouchers, saved, mock(UserRepository.class),
            actions, notifications, mock(AdminAuditService.class));

    @Test
    void withoutAModelTheRiskGrowsWithTheDaysSinceTheLastOrder() {
        assertEquals(0.25, ChurnService.ruleProbability(Map.of("recencyDays", 30.0)), 1e-9);
        assertEquals(1.0, ChurnService.ruleProbability(Map.of("recencyDays", 400.0)), 1e-9);
        assertEquals("LOW", ChurnService.level(0.2));
        assertEquals("MEDIUM", ChurnService.level(0.4));
        assertEquals("HIGH", ChurnService.level(0.75));
    }

    @Test
    void winbackPutsOnePrivateVoucherInEachWalletAndSkipsBuyersReachedRecently() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(List.of(3L)); // got one last week
        when(vouchers.save(any())).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<CurrentUser> me = mockStatic(CurrentUser.class)) {
            me.when(CurrentUser::id).thenReturn(1L);
            Map<String, Object> r = service.sendWinback(List.of(2L, 3L, 4L), 20, 40000, 0, 10);

            assertEquals(2, r.get("sent"));
            assertEquals(1, r.get("skipped"));
            ArgumentCaptor<Voucher> v = ArgumentCaptor.forClass(Voucher.class);
            verify(vouchers).save(v.capture());
            assertFalse(v.getValue().isPublicVisible());
            assertEquals(2, v.getValue().getUsageLimit());
            assertEquals(1, v.getValue().getPerUserLimit());
            assertEquals(0, BigDecimal.valueOf(20).compareTo(v.getValue().getDiscountValue()));
            assertTrue(v.getValue().getCode().startsWith("GIUCHAN"));
            verify(saved, times(2)).save(any(SavedVoucher.class));
            verify(actions, times(2)).save(any(RetentionAction.class));
            verify(notifications).notify(eq(2L), eq(NotificationType.PROMOTION), anyString(), contains("20%"), eq("/account/vouchers"));
            verify(notifications, never()).notify(eq(3L), any(), anyString(), anyString(), anyString());
        }
    }

    @Test
    void winbackRejectsAnUnreasonableDiscount() {
        assertThrows(ApiException.class, () -> service.sendWinback(List.of(2L), 80, 0, 0, 10));
        assertThrows(ApiException.class, () -> service.sendWinback(List.of(), 10, 0, 0, 10));
    }
}
