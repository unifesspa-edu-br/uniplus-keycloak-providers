package br.edu.unifesspa.uniplus.keycloak.cpfmatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CanonicalCpf")
class CanonicalCpfTest {

    @Nested
    @DisplayName("Construtor (validação)")
    class Constructor {

        @Test
        @DisplayName("aceita CPF de 11 dígitos numéricos")
        void aceita11Digitos() {
            CanonicalCpf cpf = new CanonicalCpf("12345678901");

            assertThat(cpf.value()).isEqualTo("12345678901");
        }

        @Test
        @DisplayName("aceita CPF iniciando com zero")
        void aceitaCpfComZeroAEsquerda() {
            CanonicalCpf cpf = new CanonicalCpf("01234567890");

            assertThat(cpf.value()).isEqualTo("01234567890");
            assertThat(cpf.startsWithZero()).isTrue();
        }

        @ParameterizedTest(name = "rejeita CPF com {0} dígitos")
        @ValueSource(strings = {"", "1", "1234567890", "123456789012", "12345678"})
        @DisplayName("rejeita tamanho diferente de 11")
        void rejeitaTamanhoIncorreto(String invalid) {
            assertThatThrownBy(() -> new CanonicalCpf(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11 dígitos");
        }

        @Test
        @DisplayName("rejeita null")
        void rejeitaNull() {
            assertThatThrownBy(() -> new CanonicalCpf(null))
                .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest(name = "rejeita CPF com não-dígitos: '{0}'")
        @ValueSource(strings = {"1234567890a", "12345-67890", "123 4567890", "12345.67890"})
        @DisplayName("rejeita caracteres não-numéricos")
        void rejeitaNaoDigitos(String invalid) {
            assertThatThrownBy(() -> new CanonicalCpf(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dígitos numéricos");
        }
    }

    @Nested
    @DisplayName("from (factory segura)")
    class From {

        @Test
        @DisplayName("retorna empty quando input é null")
        void inputNull() {
            assertThat(CanonicalCpf.from(null)).isEmpty();
        }

        @Test
        @DisplayName("retorna empty quando tamanho é diferente de 11")
        void tamanhoIncorreto() {
            assertThat(CanonicalCpf.from("123")).isEmpty();
            assertThat(CanonicalCpf.from("1234567890")).isEmpty();
        }

        @Test
        @DisplayName("retorna empty quando contém caracteres não-numéricos")
        void naoDigitos() {
            assertThat(CanonicalCpf.from("1234567890a")).isEmpty();
        }

        @Test
        @DisplayName("retorna Optional com instância válida quando input é canônico")
        void inputValido() {
            Optional<CanonicalCpf> result = CanonicalCpf.from("07094871422");

            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo("07094871422");
        }
    }

    @Nested
    @DisplayName("startsWithZero / truncated")
    class FallbackBehavior {

        @Test
        @DisplayName("CPF com zero à esquerda — startsWithZero true e truncated presente")
        void cpfComZero() {
            CanonicalCpf cpf = new CanonicalCpf("07094871422");

            assertThat(cpf.startsWithZero()).isTrue();
            assertThat(cpf.truncated()).contains("7094871422");
        }

        @Test
        @DisplayName("CPF sem zero à esquerda — startsWithZero false e truncated empty")
        void cpfSemZero() {
            CanonicalCpf cpf = new CanonicalCpf("76323164930");

            assertThat(cpf.startsWithZero()).isFalse();
            assertThat(cpf.truncated()).isEmpty();
        }

        @Test
        @DisplayName("truncated tem exatamente 10 dígitos")
        void truncatedTemamanho10() {
            CanonicalCpf cpf = new CanonicalCpf("01234567890");

            assertThat(cpf.truncated()).hasValueSatisfying(t -> assertThat(t).hasSize(10));
        }
    }

    @Nested
    @DisplayName("masked / toString — proteção LGPD")
    class Masking {

        @Test
        @DisplayName("masked esconde os 9 primeiros dígitos e mostra os 2 últimos")
        void maskedFormat() {
            CanonicalCpf cpf = new CanonicalCpf("12345678901");

            assertThat(cpf.masked()).isEqualTo("***.***.***-01");
        }

        @Test
        @DisplayName("toString retorna sempre o valor mascarado, nunca o CPF em texto plano")
        void toStringSempreMascarado() {
            CanonicalCpf cpf = new CanonicalCpf("99988877766");

            assertThat(cpf.toString())
                .doesNotContain("999888")
                .isEqualTo("***.***.***-66");
        }
    }

    @Nested
    @DisplayName("Imutabilidade e equals")
    class ValueSemantics {

        @Test
        @DisplayName("instâncias com mesmo valor são iguais")
        void equalsPorValor() {
            CanonicalCpf a = new CanonicalCpf("12345678901");
            CanonicalCpf b = new CanonicalCpf("12345678901");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("instâncias com valores diferentes não são iguais")
        void notEqualsPorValorDiferente() {
            CanonicalCpf a = new CanonicalCpf("12345678901");
            CanonicalCpf b = new CanonicalCpf("76323164930");

            assertThat(a).isNotEqualTo(b);
        }
    }
}
