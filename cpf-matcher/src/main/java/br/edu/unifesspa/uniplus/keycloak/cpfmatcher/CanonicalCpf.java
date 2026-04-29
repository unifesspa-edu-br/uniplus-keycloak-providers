package br.edu.unifesspa.uniplus.keycloak.cpfmatcher;

import java.util.Objects;
import java.util.Optional;

/**
 * Value object representando um CPF no formato canônico de 11 dígitos
 * (conforme Receita Federal — Lei 7.116/1983 e atualizações).
 *
 * <p>Imutável. Validação ocorre no construtor — instância só existe se for válida
 * sintaticamente (11 dígitos numéricos). Validação de DV não é feita aqui porque o
 * gov.br já entrega CPFs validados pela Receita Federal; o objetivo deste objeto é
 * apenas garantir o formato esperado em todo o pipeline de matching.
 *
 * <p>Expõe a forma {@link #truncated() truncada} (10 dígitos sem zero à esquerda)
 * usada pelo fallback contra LDAP institucional malformado.
 */
public record CanonicalCpf(String value) {

    private static final int CANONICAL_LENGTH = 11;
    private static final char LEADING_ZERO = '0';

    public CanonicalCpf {
        Objects.requireNonNull(value, "value");
        if (value.length() != CANONICAL_LENGTH) {
            throw new IllegalArgumentException(
                "CPF canônico deve ter " + CANONICAL_LENGTH + " dígitos, recebido: " + value.length());
        }
        if (!isAllDigits(value)) {
            throw new IllegalArgumentException(
                "CPF canônico só pode conter dígitos numéricos");
        }
    }

    /**
     * Cria a partir de um CPF cru (vindo do broker context, por exemplo).
     * Retorna {@link Optional#empty()} se o input não está no formato canônico —
     * permite uso seguro sem try/catch.
     */
    public static Optional<CanonicalCpf> from(String raw) {
        if (raw == null || raw.length() != CANONICAL_LENGTH || !isAllDigits(raw)) {
            return Optional.empty();
        }
        return Optional.of(new CanonicalCpf(raw));
    }

    /**
     * Indica se o CPF começa com zero — pré-condição para tentar fallback contra
     * LDAP malformado, que armazena valores como {@code int} truncado.
     */
    public boolean startsWithZero() {
        return value.charAt(0) == LEADING_ZERO;
    }

    /**
     * Forma truncada (10 dígitos sem zero à esquerda) — corresponde ao valor
     * armazenado no LDAP institucional Unifesspa para CPFs com bug histórico.
     *
     * @return optional vazio quando o CPF não começa com zero (fallback não se aplica)
     */
    public Optional<String> truncated() {
        return startsWithZero() ? Optional.of(value.substring(1)) : Optional.empty();
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mascarado para logs — apenas os 2 últimos dígitos visíveis,
     * conforme padrão do projeto Uni+ (ADR-020).
     */
    public String masked() {
        return "***.***.***-" + value.substring(CANONICAL_LENGTH - 2);
    }

    @Override
    public String toString() {
        // Nunca logar CPF completo em texto plano. Sempre mascarado.
        return masked();
    }
}
