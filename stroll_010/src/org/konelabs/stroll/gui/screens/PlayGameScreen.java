package org.konelabs.stroll.gui.screens;

import java.io.IOException;

import org.konelabs.stroll.game.Camera;
import org.konelabs.stroll.game.StrollDriver;
import org.konelabs.stroll.graphics.GBAGraphics;
import org.konelabs.stroll.graphics.GBALayer;
import org.konelabs.stroll.gui.GuiManager;
import org.konelabs.stroll.gui.GuiMessage;
import org.konelabs.stroll.gui.Screen;
import org.konelabs.stroll.system.ConfigLoader;
import org.konelabs.stroll.system.GBFont;
import org.konelabs.stroll.tetris.Block;
import org.konelabs.stroll.tetris.BlockType;
import org.konelabs.stroll.tetris.Piece;
import org.konelabs.stroll.tetris.PieceTemplate;
import org.konelabs.stroll.tetris.Tetrion;
import org.konelabs.stroll.tetris.TetrionState;

public class PlayGameScreen extends Screen {

  boolean isRunning;
  StrollDriver driver;
  Camera camera;
  GBALayer textLayer, bgLayer;
  Tetrion tetrion;

  protected PlayGameScreen(GuiManager gui) {
    super(gui);

    GBAGraphics screen = GBAGraphics.getInstance();
    bgLayer = screen.getLayer(0);
    textLayer = screen.getLayer(1);
    driver = new StrollDriver();

    camera = new Camera();
    camera.setBGLayer(bgLayer);

    int[] gravityThreshold;

    PieceTemplate[] templates;

    // load details
    try {
      // load tiles
      ConfigLoader.loadTileMemory("tileset.gif", bgLayer.tiles);
      bgLayer.setVisible(true);
      bgLayer.setAffine(true);
      bgLayer.setMode(GBALayer.Mode.TILE_MODE, 128, 128);

      // load fonts
      GBFont font = ConfigLoader.loadFonts()[0];
      for (int i = 0; i < font.tiles.length; ++i) {
        textLayer.tiles[i] = font.tiles[i];
      }
      textLayer.setVisible(true);

      // load pieces and gravity threshold
      templates = ConfigLoader.getTemplates();
      gravityThreshold = ConfigLoader.loadGravityThresholds();
      tetrion = new Tetrion(templates, gravityThreshold);

      // calibrate camera
      camera.ox = 8 * ((tetrion.getWidth() + 2) / 2);
      camera.oy = 8 * 10;

      // play game
      bgLayer.displayWraparound = false;
    } catch (IOException e) {
    }
  }

  public boolean handleMessage(GuiMessage message) {
    return driver.handleMessage(message);
  }

  public void handleFrame() {
    driver.advanceFrame(tetrion);

    int bgWidth = bgLayer.getWidth() / 8;
    Piece piece;

    // clear screen
    for (int i = 0; i < bgWidth * bgWidth; i++) {
      bgLayer.map[i] = 0;
    }

    // draw frame
    final int blockNS = 2, blockNE = 3, blockEW = 4, blockNW = 5;
    for (int i = 0; i < 19; i++) {
      int cx, cy;
      cx = 0;
      cy = i;
      bgLayer.map[cx + cy * bgWidth] = blockNS;
      cx = 11;
      bgLayer.map[cx + cy * bgWidth] = blockNS;
    }
    bgLayer.map[0 + 19 * bgWidth] = blockNE;
    bgLayer.map[11 + 19 * bgWidth] = blockNW;
    for (int i = 1; i < 11; ++i) {
      bgLayer.map[i + 19 * bgWidth] = blockEW;
    }

    // draw tetrion
    for (int x = 0; x < tetrion.getWidth(); x++) {
      for (int y = 0; y < tetrion.getHeight(); y++) {
        int cx = x + 1;
        int cy = 18 - y;

        Block block = tetrion.getPlayfield()[x + y * tetrion.getWidth()];

        if (block == null) {
          continue;
        }

        bgLayer.map[cx + cy * bgWidth] = getBlockIndex(block);
      }
    }

    // draw piece
    piece = tetrion.getPiece();
    if (piece != null) {
      for (int x = 0; x < piece.getWidth(); x++) {
        int cx = 1 + x + piece.x;
        for (int y = 0; y < piece.getHeight(); ++y) {
          boolean isBlock = piece.getBlocks()[x + y * piece.getWidth()];

          int cy = 18 - (y + piece.y);
          if (cy < 0 || cy > 18 || cx < 0 || cx > 10 || !isBlock) {
            continue;
          }

          bgLayer.map[cx + cy * bgWidth] = getPieceTypeOffset(piece.getChar()) + 1;
        }
      }
    }

    // draw effects
    camera.applyEffects();

    // display text
    print("LEVEL:" + tetrion.getLevel(), textLayer, 0, 0);
    print("LINES:" + tetrion.getLines(), textLayer, 0, 1);
    print("SCORE:" + tetrion.getScore(), textLayer, 0, 2);

    // check playing state
    if (tetrion.getState() == TetrionState.end) {
      // End game
    }
  }

  // ---------------------------- play methods ------------------------------
  private int getPieceTypeOffset(char pieceChar) {
    return 24 + (12 * getPieceTypeIndex(pieceChar));
  }

  private int getPieceTypeIndex(char pieceChar) {
    switch (Character.toUpperCase(pieceChar)) {
    case 'S':
      return 0;
    case 'Z':
      return 1;
    case 'I':
      return 2;
    case 'T':
      return 3;
    case 'O':
      return 4;
    case 'L':
      return 5;
    case 'J':
      return 6;
    default:
      return -1;
    }
  }

  private int getBlockTypeOffset(BlockType blockType) {

    switch (blockType) {
    case EMPTY:
      return -1;
    case CENTRE:
      return 1;
    case NS:
      return 2;
    case EW:
      return 3;
    case SE:
      return 4;
    case SW:
      return 5;
    case NE:
      return 6;
    case NW:
      return 7;
    case E:
      return 8;
    case S:
      return 9;
    case N:
      return 10;
    case W:
      return 11;
    default:
      return 0;
    }
  }

  private int getBlockIndex(Block block) {
    int index = getPieceTypeIndex(block.getChar());

    if (index < 0) {
      return 0;
    } else {
      int pieceTypeOffset = getPieceTypeOffset(block.getChar());
      int blockTypeOffset = getBlockTypeOffset(block.blockType);

      if (block.piece.erased) {
        blockTypeOffset = 0;
      }

      return pieceTypeOffset + blockTypeOffset;
    }
  }

  /**
   * Will write text on a particular layer
   */
  private static void print(String text, GBALayer bgLayer, int x, int y) {
    char cText[];
    int bgWidth;
    int mapPos;

    bgWidth = bgLayer.getWidth() / 8;
    cText = text.toCharArray();

    mapPos = x + (y * bgWidth);

    for (int i = 0; i < cText.length; i++) {
      bgLayer.map[i + mapPos] = cText[i] - 32;
    }
  }
}
