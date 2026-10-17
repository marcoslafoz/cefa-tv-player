package es.cefa.tv.player.discovery;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import org.springframework.stereotype.Component;

/** Comprueba si el equipo tiene alguna red con dirección IPv4 lista para usarse. */
@Component
public class NetworkProbe {

  /**
   * Indica si hay una interfaz activa, no local, con dirección IPv4.
   *
   * @return {@code true} si se puede intentar la comunicación
   */
  public boolean isAvailable() {
    try {
      return NetworkInterface.networkInterfaces()
          .filter(nic -> isUsable(nic))
          .anyMatch(nic -> nic.inetAddresses().anyMatch(Inet4Address.class::isInstance));
    } catch (SocketException e) {
      return false;
    }
  }

  private static boolean isUsable(NetworkInterface nic) {
    try {
      return nic.isUp() && !nic.isLoopback() && !nic.isVirtual();
    } catch (SocketException e) {
      return false;
    }
  }
}
