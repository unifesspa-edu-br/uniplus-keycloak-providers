package br.edu.unifesspa.uniplus.keycloak.cpfmatcher;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Factory do {@link CpfMatcherAuthenticator}. Registrada via SPI Java
 * ({@code META-INF/services/org.keycloak.authentication.AuthenticatorFactory})
 * e exposta na console admin do Keycloak como execution disponível para flows.
 */
public final class CpfMatcherAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "uniplus-cpf-matcher";

    private static final CpfMatcherAuthenticator SINGLETON = new CpfMatcherAuthenticator();

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.ALTERNATIVE,
        AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Uni+ — Detect Existing Broker User by CPF";
    }

    @Override
    public String getReferenceCategory() {
        return "broker";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Identifica user existente no realm por matching de CPF (atributo 'cpf'). "
            + "Usa o claim 'sub' do broker como CPF de 11 dígitos canônico, com fallback "
            + "para 10 dígitos quando o LDAP institucional tem o valor truncado (zero à "
            + "esquerda removido). Aplica auto-heal do atributo ao linkar via fallback.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // Sem configuração externa.
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Sem inicialização pós-arranque.
    }

    @Override
    public void close() {
        // Sem recursos a liberar.
    }
}
