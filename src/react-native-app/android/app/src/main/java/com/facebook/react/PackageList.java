package com.facebook.react;

import java.util.ArrayList;
import java.util.List;

/**
 * CI fallback for environments where RNGP autolinking config cannot be resolved.
 */
public class PackageList {
  private final ReactNativeHost reactNativeHost;

  public PackageList(ReactNativeHost reactNativeHost) {
    this.reactNativeHost = reactNativeHost;
  }

  public List<ReactPackage> getPackages() {
    return new ArrayList<>();
  }
}
