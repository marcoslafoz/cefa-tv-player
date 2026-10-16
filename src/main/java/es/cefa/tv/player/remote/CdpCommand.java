package es.cefa.tv.player.remote;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Comando del protocolo DevTools listo para enviarse.
 *
 * @param method nombre del método
 * @param params parámetros
 */
public record CdpCommand(String method, ObjectNode params) {}
