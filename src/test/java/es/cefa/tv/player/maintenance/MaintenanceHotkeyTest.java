package es.cefa.tv.player.maintenance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.cefa.tv.player.browser.CdpClient;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaintenanceHotkeyTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private CdpClient cdp;
  private MaintenanceService maintenance;
  private MaintenanceHotkey hotkey;

  @BeforeEach
  void setUp() {
    cdp = mock(CdpClient.class);
    when(cdp.params()).thenAnswer(call -> mapper.createObjectNode());
    maintenance = mock(MaintenanceService.class);
    hotkey = new MaintenanceHotkey(cdp, maintenance);
  }

  @Test
  void enterMaintenanceWhenThePageCallsTheBinding() {
    ObjectNode params = mapper.createObjectNode().put("name", MaintenanceHotkey.BINDING);

    hotkey.onCdpEvent("Runtime.bindingCalled", params);

    verify(maintenance).enter();
  }

  @Test
  void ignoresOtherBindingsAndEvents() {
    hotkey.onCdpEvent("Runtime.bindingCalled", mapper.createObjectNode().put("name", "otro"));
    hotkey.onCdpEvent("Page.loadEventFired", mapper.createObjectNode());

    verify(maintenance, never()).enter();
  }

  @Test
  void installsTheScriptOnceAndAgainAfterADisconnection() throws Exception {
    hotkey.ensureInstalled();
    hotkey.ensureInstalled();
    verify(cdp, times(1)).call(eq("Runtime.addBinding"), any());
    verify(cdp, times(1)).call(eq("Page.addScriptToEvaluateOnNewDocument"), any());

    hotkey.onCdpEvent("Cefa.disconnected", mapper.createObjectNode());
    hotkey.ensureInstalled();
    verify(cdp, times(2)).call(eq("Runtime.addBinding"), any());
  }

  @Test
  void retriesLaterWhenChromiumIsNotAvailable() throws Exception {
    when(cdp.call(eq("Runtime.enable"), any())).thenThrow(new IOException("sin navegador"));
    hotkey.ensureInstalled();

    when(cdp.call(eq("Runtime.enable"), any())).thenReturn(mapper.createObjectNode());
    hotkey.ensureInstalled();

    verify(cdp, times(1)).call(eq("Runtime.addBinding"), any());
  }

  @Test
  void doesNotInstallWhileInMaintenance() throws Exception {
    when(maintenance.isActive()).thenReturn(true);

    hotkey.ensureInstalled();

    verify(cdp, never()).call(any(), any());
  }
}
