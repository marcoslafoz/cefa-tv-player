package es.cefa.tv.player.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import es.cefa.tv.player.shared.config.PlayerProperties;
import es.cefa.tv.player.sync.PlayerSocket;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ShellServiceTest {

  private final List<Map<String, Object>> sent = new CopyOnWriteArrayList<>();
  private final PlayerSocket socket = mock(PlayerSocket.class);

  @BeforeEach
  void captureMessages() {
    Mockito.doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> message = (Map<String, Object>) invocation.getArgument(0);
              sent.add(message);
              return null;
            })
        .when(socket)
        .sendText(Mockito.any());
  }

  private ShellService service(boolean enabled, ShellLauncher launcher) {
    PlayerProperties properties =
        new PlayerProperties(
            java.nio.file.Path.of("."),
            "",
            "",
            47808,
            30,
            null,
            "headless",
            new PlayerProperties.Shell(enabled, "container"));
    return new ShellService(properties, socket, launcher);
  }

  private static ShellLauncher sh(String script) {
    return (cols, rows) -> start(cols, rows, "/bin/sh", "-c", script);
  }

  private static PtyProcess start(int cols, int rows, String... command) throws IOException {
    return new PtyProcessBuilder(command)
        .setInitialColumns(cols)
        .setInitialRows(rows)
        .setConsole(false)
        .start();
  }

  private String output(String sessionId) {
    StringBuilder text = new StringBuilder();
    sent.stream()
        .filter(
            message ->
                "SHELL_OUTPUT".equals(message.get("type"))
                    && sessionId.equals(message.get("sessionId")))
        .forEach(message -> text.append(message.get("data")));
    return text.toString();
  }

  private boolean exited(String sessionId) {
    return sent.stream()
        .anyMatch(
            message ->
                "SHELL_EXIT".equals(message.get("type"))
                    && sessionId.equals(message.get("sessionId")));
  }

  @Test
  void runsCommandInPseudoterminalAndReportsExitCode() {
    ShellService service = service(true, sh("echo hola-pty; exit 3"));

    service.open("s1", 100, 30);

    await().atMost(Duration.ofSeconds(10)).until(() -> exited("s1"));
    assertThat(output("s1")).contains("hola-pty");
    assertThat(sent)
        .anyMatch(
            m -> "SHELL_EXIT".equals(m.get("type")) && Integer.valueOf(3).equals(m.get("code")));
  }

  @Test
  void forwardsInputAndResizesTerminal() {
    ShellService service = service(true, sh("stty -echo; read line; echo got:$line; stty size"));

    service.open("s2", 100, 30);
    service.input("s2", "ping\n");

    await().atMost(Duration.ofSeconds(10)).until(() -> output("s2").contains("got:ping"));
    await().atMost(Duration.ofSeconds(10)).until(() -> exited("s2"));
    assertThat(output("s2")).contains("30 100");
  }

  @Test
  void closeKillsTheProcessAndReportsExit() {
    ShellService service = service(true, sh("sleep 60"));

    service.open("s3", 80, 24);
    service.close("s3");

    await().atMost(Duration.ofSeconds(10)).until(() -> exited("s3"));
  }

  @Test
  void rejectsSessionsWhenShellIsDisabled() {
    ShellService service = service(false, sh("echo no"));

    service.open("s4", 80, 24);

    assertThat(sent)
        .anyMatch(
            m ->
                "SHELL_EXIT".equals(m.get("type"))
                    && "SHELL_DISABLED".equals(m.get("error"))
                    && "s4".equals(m.get("sessionId")));
  }

  @Test
  void rejectsSecondSessionWhileOneIsActive() {
    ShellService service = service(true, sh("sleep 60"));

    service.open("a", 80, 24);
    service.open("b", 80, 24);

    assertThat(sent)
        .anyMatch(m -> "SHELL_EXIT".equals(m.get("type")) && "SHELL_BUSY".equals(m.get("error")));
    service.closeAll();
    await().atMost(Duration.ofSeconds(10)).until(() -> exited("a"));
  }

  @Test
  void closeAllEndsTheSessionWhenTheConnectionDrops() {
    ShellService service = service(true, sh("sleep 60"));
    service.open("s5", 80, 24);

    service.closeAll();

    await().atMost(Duration.ofSeconds(10)).until(() -> exited("s5"));
    service.open("s6", 80, 24);
    service.closeAll();
    await().atMost(Duration.ofSeconds(10)).until(() -> exited("s6"));
  }

  @Test
  void reportsLaunchFailure() {
    ShellService service =
        service(
            true,
            (cols, rows) -> {
              throw new IOException("sin shell");
            });

    service.open("s7", 80, 24);

    assertThat(sent).anyMatch(m -> "SHELL_LAUNCH_FAILED".equals(m.get("error")));
  }
}
