package es.cefa.tv.player.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcFilesTest {

  @Test
  void readsMemoryFromMeminfo() {
    String meminfo =
        "MemTotal:        8123456 kB\nMemFree:          123456 kB\nMemAvailable:    4000000 kB\n";

    assertThat(ProcFiles.memTotalKb(meminfo)).hasValue(8123456);
    assertThat(ProcFiles.memAvailableKb(meminfo)).hasValue(4000000);
    assertThat(ProcFiles.memAvailableKb("MemTotal: 10 kB\n")).isEmpty();
  }

  @Test
  void readsCpuModelFromX86AndArm() {
    assertThat(ProcFiles.cpuModel("processor\t: 0\nmodel name\t: Intel(R) N100\n"))
        .isEqualTo("Intel(R) N100");
    assertThat(
            ProcFiles.cpuModel("processor : 0\nHardware\t: BCM2711\nModel\t\t: Raspberry Pi 4\n"))
        .isEqualTo("BCM2711");
    assertThat(ProcFiles.cpuModel("processor : 0\n")).isNull();
  }

  @Test
  void readsPrettyNameFromOsRelease() {
    assertThat(
            ProcFiles.prettyName(
                "NAME=\"Debian\"\nPRETTY_NAME=\"Debian GNU/Linux 12 (bookworm)\"\n"))
        .isEqualTo("Debian GNU/Linux 12 (bookworm)");
  }

  @Test
  void computesCpuTicksIncludingIdleAndIowait() {
    long[] ticks = ProcFiles.cpuTicks("cpu  100 0 50 800 50 0 0 0 0 0\ncpu0 1 1 1 1 1 1 1 1 1 1\n");

    assertThat(ticks).containsExactly(1000, 850);
    assertThat(ProcFiles.cpuTicks("intr 12345\n")).isEmpty();
  }

  @Test
  void readsLoadAndUptime() {
    assertThat(ProcFiles.load1("0.42 0.30 0.25 1/300 12345\n")).hasValue(0.42);
    assertThat(ProcFiles.uptimeSec("12345.67 54321.00\n")).hasValue(12345);
    assertThat(ProcFiles.load1("")).isEmpty();
  }

  @Test
  void convertsThermalZoneAndRejectsImplausibleValues() {
    assertThat(ProcFiles.temperature("48000\n")).hasValue(48.0);
    assertThat(ProcFiles.temperature("-274000")).isEmpty();
    assertThat(ProcFiles.temperature("n/a")).isEmpty();
  }
}
