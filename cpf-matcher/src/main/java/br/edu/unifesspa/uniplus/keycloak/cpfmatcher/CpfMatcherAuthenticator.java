package br.edu.unifesspa.uniplus.keycloak.cpfmatcher;

import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.ExistingUserInfo;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Authenticator de first-broker-login que casa identidades federadas (gov.br)
 * com users existentes no realm pelo atributo {@code cpf}, com tolerância para
 * o bug histórico do LDAP institucional Unifesspa (CPF de 10 dígitos sem zero à
 * esquerda).
 *
 * <p>Justificativa, fluxo de matching e limitações estão documentados em
 * {@code uniplus-keycloak-providers#1} e na ADR-029 (uniplus-docs).
 *
 * <p>Esta classe orquestra; a lógica do domínio CPF está em {@link CanonicalCpf}.
 */
public final class CpfMatcherAuthenticator extends AbstractIdpAuthenticator {

    private static final Logger LOG = Logger.getLogger(CpfMatcherAuthenticator.class);

    /** Nome do atributo do user no realm que armazena o CPF. */
    public static final String CPF_USER_ATTRIBUTE = "cpf";

    @Override
    protected void authenticateImpl(AuthenticationFlowContext context,
                                    SerializedBrokeredIdentityContext serializedCtx,
                                    BrokeredIdentityContext brokerContext) {
        Optional<CanonicalCpf> cpfFromBroker = CanonicalCpf.from(brokerContext.getId());

        if (cpfFromBroker.isEmpty()) {
            LOG.debugf("CPF matcher [%s]: 'sub' do broker ausente ou fora do formato canônico — delegando ao próximo executor",
                brokerContext.getIdpConfig().getAlias());
            context.attempted();
            return;
        }

        CanonicalCpf cpf = cpfFromBroker.get();
        Optional<MatchResult> match = findMatchingUser(context, cpf);

        if (match.isPresent()) {
            MatchResult result = match.get();
            if (result.viaFallback()) {
                LOG.infof("CPF matcher: matching via fallback (LDAP malformado) — username='%s', aplicando auto-heal",
                    result.user().getUsername());
                applyAutoHeal(result.user(), cpf);
            } else {
                LOG.infof("CPF matcher: matching direto (formato canônico) — username='%s'",
                    result.user().getUsername());
            }
            registerExistingUser(context, result.user());
        } else {
            LOG.debug("CPF matcher: nenhum user existente — delegando ao próximo executor para criação ou matching alternativo");
        }

        // Sempre attempted() — segue padrão do idp-detect-existing-broker-user.
        // O próximo executor do flow (autolink ou criação) usa o EXISTING_USER_INFO
        // (quando registrado) para decidir o que fazer.
        context.attempted();
    }

    @Override
    protected void actionImpl(AuthenticationFlowContext context,
                              SerializedBrokeredIdentityContext serializedCtx,
                              BrokeredIdentityContext brokerContext) {
        // Não há form interativo neste Authenticator. O Keycloak chama actionImpl
        // se houver retomada do flow após uma submission — repete a lógica de
        // matching para que o auth note fique consistente.
        authenticateImpl(context, serializedCtx, brokerContext);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void close() {
        // Sem recursos a liberar.
    }

    // ---- helpers ----

    /**
     * Busca um user pelo CPF canônico. Se nada for encontrado e o CPF iniciar com
     * zero, tenta fallback pela forma truncada (10 dígitos) — caso típico do LDAP
     * institucional malformado. Retorna a tentativa que sucedeu, ou empty.
     */
    Optional<MatchResult> findMatchingUser(AuthenticationFlowContext context, CanonicalCpf cpf) {
        UserModel direct = findUserByAttribute(context, CPF_USER_ATTRIBUTE, cpf.value());
        if (direct != null) {
            return Optional.of(new MatchResult(direct, /* viaFallback */ false));
        }

        return cpf.truncated()
            .map(truncated -> findUserByAttribute(context, CPF_USER_ATTRIBUTE, truncated))
            .filter(java.util.Objects::nonNull)
            .map(user -> new MatchResult(user, /* viaFallback */ true));
    }

    private UserModel findUserByAttribute(AuthenticationFlowContext context,
                                          String attributeName, String attributeValue) {
        return context.getSession().users()
            .searchForUserByUserAttributeStream(context.getRealm(), attributeName, attributeValue)
            .findFirst()
            .orElse(null);
    }

    /**
     * Atualiza o atributo {@code cpf} do user para o formato canônico (11 dígitos).
     * Em users LDAP-federados read-only, esta escrita afeta apenas o cache local
     * do Keycloak — o LDAP institucional permanece com o valor truncado original
     * (correção definitiva exige migração no próprio LDAP).
     */
    void applyAutoHeal(UserModel user, CanonicalCpf canonicalCpf) {
        user.setSingleAttribute(CPF_USER_ATTRIBUTE, canonicalCpf.value());
    }

    /**
     * Registra o user existente no auth note {@code EXISTING_USER_INFO}, formato
     * esperado pelos executors padrão do flow first-broker-login (autolink ou
     * confirmação de link).
     */
    void registerExistingUser(AuthenticationFlowContext context, UserModel existingUser) {
        String cpfAttribute = existingUser.getFirstAttribute(CPF_USER_ATTRIBUTE);
        ExistingUserInfo info = new ExistingUserInfo(
            existingUser.getId(),
            CPF_USER_ATTRIBUTE,
            cpfAttribute);
        context.getAuthenticationSession()
            .setAuthNote(AbstractIdpAuthenticator.EXISTING_USER_INFO, info.serialize());
    }

    /** Resultado do matching: user encontrado e se foi via fallback (auto-heal needed). */
    record MatchResult(UserModel user, boolean viaFallback) {
        MatchResult {
            java.util.Objects.requireNonNull(user, "user");
        }
    }
}
