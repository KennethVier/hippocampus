package com.hippocampus.materials.domain;
import static org.assertj.core.api.Assertions.assertThat; import org.junit.jupiter.api.Test;
class DeterministicChunkTokenCounterTests {private final DeterministicChunkTokenCounter c=new DeterministicChunkTokenCounter();
 @Test void countsUnicodeRunsPunctuationAndWhitespaceDeterministically(){assertThat(c.count("abcd efgh!")).isEqualTo(3);assertThat(c.count("β1 Ca2+ 👩‍⚕️")).isPositive();assertThat(c.count(" \n\t")).isZero();assertThat(c.count("HLA-B27")).isEqualTo(3);}
}
