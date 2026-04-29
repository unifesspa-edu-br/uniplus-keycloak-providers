package br.edu.unifesspa.uniplus.keycloak.cpfmatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CpfMatcherAuthenticator")
class CpfMatcherAuthenticatorTest {

    private static final String CPF_CANONICAL_COM_ZERO = "07094871422";
    private static final String CPF_TRUNCATED = "7094871422";
    private static final String CPF_NORMAL = "76323164930";
    private static final String CPF_INEXISTENTE_SEM_ZERO = "12345678909";

    @Mock private AuthenticationFlowContext context;
    @Mock private BrokeredIdentityContext brokerContext;
    @Mock private SerializedBrokeredIdentityContext serializedCtx;
    @Mock private KeycloakSession session;
    @Mock private RealmModel realm;
    @Mock private UserProvider userProvider;
    @Mock private AuthenticationSessionModel authSession;
    @Mock private IdentityProviderModel idpConfig;
    @Mock private UserModel existingUser;

    private CpfMatcherAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new CpfMatcherAuthenticator();

        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(session.users()).thenReturn(userProvider);
        when(brokerContext.getIdpConfig()).thenReturn(idpConfig);
        when(idpConfig.getAlias()).thenReturn("govbr");
    }

    @Nested
    @DisplayName("Cenários funcionais")
    class FunctionalScenarios {

        @Test
        @DisplayName("authenticateImpl_quandoCpfCanonicoEncontradoDireto_deveRegistrarExistingUserSemAutoHeal")
        void cenario1_matchDireto() {
            when(brokerContext.getId()).thenReturn(CPF_NORMAL);
            mockUserFound(CPF_NORMAL, existingUser);

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            verify(authSession).setAuthNote(eq(AbstractIdpAuthenticator.EXISTING_USER_INFO), any());
            verify(existingUser, never()).setSingleAttribute(any(), any());
            verify(context).attempted();
            verify(context, never()).success();
            verify(context, never()).setUser(any());
        }

        @Test
        @DisplayName("authenticateImpl_quandoEncontradoViaFallback_deveAplicarAutoHealEEgistrar")
        void cenario2_fallbackComAutoHeal() {
            when(brokerContext.getId()).thenReturn(CPF_CANONICAL_COM_ZERO);
            mockUserNotFound(CPF_CANONICAL_COM_ZERO);
            mockUserFound(CPF_TRUNCATED, existingUser);

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            verify(existingUser).setSingleAttribute("cpf", CPF_CANONICAL_COM_ZERO);
            verify(authSession).setAuthNote(eq(AbstractIdpAuthenticator.EXISTING_USER_INFO), any());
            verify(context).attempted();
        }

        @Test
        @DisplayName("authenticateImpl_quandoNenhumMatch_deveDelegarSemRegistrar")
        void cenario3_userNovo() {
            when(brokerContext.getId()).thenReturn(CPF_CANONICAL_COM_ZERO);
            mockUserNotFound(CPF_CANONICAL_COM_ZERO);
            mockUserNotFound(CPF_TRUNCATED);

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            verify(authSession, never())
                .setAuthNote(eq(AbstractIdpAuthenticator.EXISTING_USER_INFO), any());
            verify(existingUser, never()).setSingleAttribute(any(), any());
            verify(context).attempted();
        }
    }

    @Nested
    @DisplayName("Decisão de fallback")
    class FallbackDecision {

        @Test
        @DisplayName("authenticateImpl_quandoCpfNaoComecaComZero_naoTentaFallback")
        void naoTentaFallbackSemZero() {
            when(brokerContext.getId()).thenReturn(CPF_INEXISTENTE_SEM_ZERO);
            mockUserNotFound(CPF_INEXISTENTE_SEM_ZERO);

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            verify(userProvider, times(1)).searchForUserByUserAttributeStream(any(), any(), any());
            verify(authSession, never())
                .setAuthNote(eq(AbstractIdpAuthenticator.EXISTING_USER_INFO), any());
            verify(context).attempted();
        }

        @Test
        @DisplayName("authenticateImpl_quandoCpfComeçaComZeroMasMatchDireto_naoTentaFallback")
        void naoTentaFallbackQuandoMatchDireto() {
            when(brokerContext.getId()).thenReturn(CPF_CANONICAL_COM_ZERO);
            mockUserFound(CPF_CANONICAL_COM_ZERO, existingUser);

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            // Apenas 1 busca (canônico). Fallback NÃO consultado.
            verify(userProvider, times(1)).searchForUserByUserAttributeStream(any(), any(), any());
            verify(existingUser, never()).setSingleAttribute(any(), any());
        }
    }

    @Nested
    @DisplayName("Validação de input do broker")
    class InputValidation {

        @Test
        @DisplayName("authenticateImpl_quandoCpfNulo_deveDelegarSemBuscar")
        void cpfNulo() {
            when(brokerContext.getId()).thenReturn(null);

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            verify(userProvider, never()).searchForUserByUserAttributeStream(any(), any(), any());
            verify(context).attempted();
        }

        @Test
        @DisplayName("authenticateImpl_quandoCpfEmBranco_deveDelegarSemBuscar")
        void cpfEmBranco() {
            when(brokerContext.getId()).thenReturn("           ");

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            verify(userProvider, never()).searchForUserByUserAttributeStream(any(), any(), any());
            verify(context).attempted();
        }

        @Test
        @DisplayName("authenticateImpl_quandoCpfFormatoInvalido_deveDelegarSemBuscar")
        void cpfTamanhoErrado() {
            when(brokerContext.getId()).thenReturn("1234567890");  // 10 dígitos no broker é inválido

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            verify(userProvider, never()).searchForUserByUserAttributeStream(any(), any(), any());
            verify(context).attempted();
        }

        @Test
        @DisplayName("authenticateImpl_quandoCpfComCaracteresInvalidos_deveDelegarSemBuscar")
        void cpfNaoNumerico() {
            when(brokerContext.getId()).thenReturn("1234567890a");

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            verify(userProvider, never()).searchForUserByUserAttributeStream(any(), any(), any());
            verify(context).attempted();
        }
    }

    @Nested
    @DisplayName("Auto-heal")
    class AutoHeal {

        @Test
        @DisplayName("autoHeal_escreveCpfCanonicoComOnzeDigitos")
        void autoHealEscreveCanonico() {
            when(brokerContext.getId()).thenReturn(CPF_CANONICAL_COM_ZERO);
            mockUserNotFound(CPF_CANONICAL_COM_ZERO);
            mockUserFound(CPF_TRUNCATED, existingUser);

            authenticator.authenticateImpl(context, serializedCtx, brokerContext);

            ArgumentCaptor<String> attrName = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> attrValue = ArgumentCaptor.forClass(String.class);
            verify(existingUser).setSingleAttribute(attrName.capture(), attrValue.capture());

            assertThat(attrName.getValue()).isEqualTo("cpf");
            assertThat(attrValue.getValue())
                .hasSize(11)
                .startsWith("0")
                .isEqualTo(CPF_CANONICAL_COM_ZERO);
        }
    }

    @Nested
    @DisplayName("Contrato do AuthenticatorFactory")
    class FactoryContract {

        @Test
        @DisplayName("requiresUser_deveSerFalse")
        void requiresUserDeveSerFalse() {
            assertThat(authenticator.requiresUser()).isFalse();
        }

        @Test
        @DisplayName("configuredFor_deveSerSempreTrue")
        void configuredForSempreTrue() {
            assertThat(authenticator.configuredFor(session, realm, existingUser)).isTrue();
        }
    }

    // ---- helpers ----

    private void mockUserFound(String cpfValue, UserModel user) {
        when(userProvider.searchForUserByUserAttributeStream(realm, "cpf", cpfValue))
            .thenReturn(Stream.of(user));
    }

    private void mockUserNotFound(String cpfValue) {
        when(userProvider.searchForUserByUserAttributeStream(realm, "cpf", cpfValue))
            .thenReturn(Stream.empty());
    }
}
