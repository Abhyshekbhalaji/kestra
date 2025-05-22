# JMH-Benchmarks module

This module contains benchmarks written using JMH from OpenJDK.

## Running Benchmarks

**To run all benchmarks**

```bash
./gradlew jmh:jmh
```

**To run a specific benchmark**

```bash
./gradlew jmh:jmh -Pjmh.include=io.kestra.core.utils.MapUtilsBenchmark
```