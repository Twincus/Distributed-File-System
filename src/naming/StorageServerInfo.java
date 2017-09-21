package naming;

import storage.Command;
import storage.Storage;

/**
 * Contains information about the storage server.
 */
public class StorageServerInfo {
  Storage clientStub; // for client
  Command commandStub; // for naming server

  public StorageServerInfo(Storage clientStub, Command commandStub) {
    this.clientStub = clientStub;
    this.commandStub = commandStub;
  }

  @Override
  public int hashCode() {
    return clientStub.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    StorageServerInfo ssInfo = (StorageServerInfo) other;
    return (ssInfo.clientStub == ssInfo.clientStub);
  }
}
