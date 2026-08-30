package com.b44t.messenger;

/** Minimal JNI type required by the official account-manager bindings. */
public final class DcContext {
  private long contextCPtr;

  DcContext(long contextCPtr) {
    this.contextCPtr = contextCPtr;
  }
}
