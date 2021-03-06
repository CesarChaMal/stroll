package org.konelabs.stroll.gui;

import org.konelabs.stroll.graphics.GBAGraphics;
import org.konelabs.stroll.system.Input;

public class GuiManager {
  private Screen currentScreen;

  public GuiManager() {
    currentScreen = null;
  }

  public void openScreen(Screen screen) {
    if (null != currentScreen) {
      currentScreen.closePage();
    }

    currentScreen = screen;
  }

  public void monitor() {
    while (true) {
      readKeys();
      updateFrame();
    }
  }

  private final void updateFrame() {
    if (null != currentScreen) {
      currentScreen.update();
      GBAGraphics.getInstance().waitVBL();
    }
  }

  private final void readKeys() {
    Input.updateKeys();

    if (0 != Input.getPressedButtons()) {
      currentScreen.handleMessage(GuiMessage.createButtonDownMessage(Input
          .getPressedButtons()));
    }

    if (0 != Input.getReleasedButtons()) {
      currentScreen.handleMessage(GuiMessage.createButtonUpMessage(Input
          .getReleasedButtons()));
    }

    if (0 != Input.getPressedKeys()) {
      currentScreen.handleMessage(GuiMessage.createKeyDownMessage(
          Input.getPressedKeys(), false));
    }

    if (0 != Input.getReleasedKeys()) {
      currentScreen.handleMessage(GuiMessage.createKeyUpMessage(Input
          .getReleasedKeys()));
    }
  }
}
