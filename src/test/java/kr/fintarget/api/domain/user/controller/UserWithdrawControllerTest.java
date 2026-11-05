package kr.fintarget.api.domain.user.controller;

import kr.fintarget.api.domain.user.UserController;
import kr.fintarget.api.domain.user.service.UserService;
import kr.fintarget.api.security.BlacklistedToken;
import kr.fintarget.api.security.BlacklistedTokenRepository;
import kr.fintarget.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserWithdrawControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @InjectMocks
    private UserController userController;

    private void givenToken(String token, String jti, String userId) {
        when(jwtUtil.getJti(token)).thenReturn(jti);
        when(jwtUtil.getUserId(token)).thenReturn(userId);
    }

    @Test
    void 탈퇴하면_액세스_토큰과_바디의_리프레시_토큰을_모두_블랙리스트에_넣는다() {
        givenToken("access", "jti-access", "user-1");
        givenToken("refresh", "jti-refresh", "user-1");
        when(jwtUtil.getExpirationTime(any())).thenReturn(LocalDateTime.now().plusDays(7));

        userController.withdraw("user-1", "Bearer access", Map.of("refreshToken", "refresh"));

        verify(userService).withdraw("user-1");
        ArgumentCaptor<BlacklistedToken> saved = ArgumentCaptor.forClass(BlacklistedToken.class);
        verify(blacklistedTokenRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(BlacklistedToken::getJti).containsExactly("jti-access", "jti-refresh");
    }

    @Test
    void 바디가_없어도_액세스_토큰만_무효화하고_정상_처리된다() {
        givenToken("access", "jti-access", "user-1");
        when(jwtUtil.getExpirationTime("access")).thenReturn(LocalDateTime.now().plusHours(1));

        userController.withdraw("user-1", "Bearer access", null);

        verify(blacklistedTokenRepository, times(1)).save(any());
    }

    @Test
    void 다른_사용자의_리프레시_토큰은_블랙리스트에_넣지_않는다() {
        givenToken("others-refresh", "jti-other", "user-2");

        userController.withdraw("user-1", null, Map.of("refreshToken", "others-refresh"));

        verify(blacklistedTokenRepository, never()).save(any());
    }

    @Test
    void 파싱할_수_없는_리프레시_토큰은_무시하고_탈퇴는_완료된다() {
        when(jwtUtil.getJti("broken")).thenThrow(new io.jsonwebtoken.MalformedJwtException("broken"));

        userController.withdraw("user-1", null, Map.of("refreshToken", "broken"));

        verify(userService).withdraw("user-1");
        verify(blacklistedTokenRepository, never()).save(any());
    }
}
