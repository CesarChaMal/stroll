package org.konelabs.stroll.graphics;

import org.konelabs.stroll.lang.IPlayClient;

/**
 * <p>
 * Represents a virtual screen; this base class contains all of the
 * synchronisation methods to maintain a constant speed for the screen as well
 * as managing the interrupts.
 * </p>
 */
public abstract class AbstractGraphics<DLayer extends AbstractGBALayer>
    implements IPlayClient, Runnable {
  // ********************************* PRIVATE VARIABLES ********************
  // ************************************************************************

  // ________________________________________________________________________
  // display registers (width,height,vCount,buffer,hbl/vblEnabled,isRunning)
  /** width and height of screen in pixels */
  private int width, height;
  /** current line being drawn */
  private int vCount;
  /** direct screen buffer */
  private int buffer[];

  /**
   * stores whether hbl and vbl interrupts are allowed and if the screen is
   * currently running
   */
  private boolean hblEnabled, vblEnabled, isRunning;

  // ________________________________________________________________________
  // threads ----------------------------------------------------------------
  /** vblThread runs a timer to synchronise screen drawing */
  private Runnable vblThread;
  /** used for synchronisation of vblWait method */
  private Object vblWaitThread;

  // ________________________________________________________________________
  // listeners --------------------------------------------------------------
  /**
   * screenViewer is updated when each screen frame is completed in the buffer.
   */
  private IGBAGraphicsViewer screenViewer;
  /** interrupt listeners can be called for hBlank effects */
  private InterruptListener vblListener, hblListener;

  // ********************************* PRIVATE METHODS **********************
  // ************************************************************************
  /**
   * Will start the synchronisation threads that will keep the frame rates as
   * constant as possible
   */
  private void initializeThreads(final Object vblAwaken) {
    vblThread = new Runnable() {
      public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while (isRunning) {
          try {
            Thread.sleep(1000 / 60);
          } catch (InterruptedException e) {
          }

          synchronized (vblAwaken) {
            vblAwaken.notify();
          }
        }

        synchronized (vblAwaken) {
          vblAwaken.notify();
        }
      }
    };

    new Thread(vblThread).start();
  } // initializeThreads(Object)

  /**
   * Notifies hblListener of a HBL interrupt
   */
  private void callHBL() {
    if (isHBLEnabled() & hblListener != null) {
      hblListener.screenInterrupt(InterruptListener.HBL_INTERRUPT);

    }
  } // callHBL ()

  /** Notifies VBL listener of VBL and awakens waitVBL thread */
  private void callVBL() {
    // notify waitVBL method
    if (this.vblWaitThread != null) {
      synchronized (this.vblWaitThread) {
        this.vblWaitThread.notify();
      }
    }

    // call vbl listener
    if (isVBLEnabled() & vblListener != null) {
      vblListener.screenInterrupt(InterruptListener.VBL_INTERRUPT);
    }
  } // callVBL ()

  // ********************************* PROTECTED METHODS ********************
  // ************************************************************************
  protected AbstractGraphics(int width, int height) {
    this.width = width;
    this.height = height;

    this.buffer = new int[width * height];
    this.vCount = 0;
  } // AbstractScreen (int,int)

  /**
   * will draw all of the Screen's layers to the specified buffer on the current
   * line.
   */
  protected abstract void drawLayers(int buffer[]);

  // ********************************** PUBLIC METHODS **********************
  // ************************************************************************

  /** returns the width of the screen */
  public int getWidth() {
    return this.width;
  } // getWidth ()

  /** returns the height of the screen */
  public int getHeight() {
    return this.height;
  } // getHeight ()

  /** returns the vertical line */
  public int getVCount() {
    return this.vCount;
  } // getVCount ()

  /**
   * Initiates the screen's drawing methods, and synchronises itself with the
   * vertical blank (60hz).
   */
  public void run() {
    initializeThreads(this);
    while (this.isRunning) {
      // render frame
      for (int y = 0; y < getHeight(); ++y) {
        // yRef is the position of this row in the screen buffer
        int yRef = y * getWidth();

        // clear line
        for (int x = 0; x < getWidth(); ++x) {
          buffer[x + yRef] = 0xff000000;
        }

        // update screen y
        this.vCount = y;

        // draw current line
        drawLayers(this.buffer);
        callHBL();
      }

      if (screenViewer != null) {
        screenViewer.screenUpdated();
      }

      // inform listeners
      callVBL();

      // wait for VBL sync
      synchronized (this) {
        try {
          wait();
        } catch (InterruptedException e) {
        }
      }

    }
  } // run ()

  /**
   * returns the associated screen viewer. The screen viewer is called when the
   * screen is updated, and registers its bitmap buffer.
   */
  public IGBAGraphicsViewer getScreenViewer() {
    return this.screenViewer;
  } // getScreenViewer ()

  /** returns whether HBL is enabled */
  public boolean isHBLEnabled() {
    return this.hblEnabled;
  } // isHBLEnabled ()

  /** returns whether VBL is enabled */
  public boolean isVBLEnabled() {
    return this.vblEnabled;
  } // isVBLEnabled ()

  /** returns whether HBL is enabled */
  public void setHBLEnable(boolean status) {
    this.hblEnabled = status;
  } // setHBLEnable (boolean)

  /** sets the horizontal blank listener */
  public void setHBLListener(InterruptListener listener) {
    this.hblListener = listener;
  } // setHBLListener (InterruptListener)

  /**
   * sets the listener to the screen. The listener will be notified of each
   * screen redraw
   * 
   * @param viewer
   */
  public void setScreenViewer(IGBAGraphicsViewer viewer) {
    this.screenViewer = viewer;

    if (this.screenViewer != null) {
      this.screenViewer.registerBuffer(buffer);
    }
  } // setScreenViewer (IScreenViewer)

  /** returns whether VBL is enabled */
  public void setVBLEnable(boolean status) {
    this.vblEnabled = status;
  } // setVBLEnable (boolean)

  /** sets the vertical blank listener */
  public void setVBLListener(InterruptListener listener) {
    this.vblListener = listener;
  } // setVBLListener (InterruptListener)

  /**
   * Makes thread of the calling method wait for the vertical blank.
   */
  public void waitVBL() {
    // return if the screen is not running
    if (!this.isRunning) {
      return;
    }

    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    this.vblWaitThread = new Object();
    synchronized (this.vblWaitThread) {
      try {
        this.vblWaitThread.wait();
        this.vblWaitThread = null;
      } catch (InterruptedException e) {
      }
    }

    Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
  } // waitVBL ()

  // ********************************* ABSTRACT METHODS *********************
  // ************************************************************************

  /** returns the given layer; null is returned if the layer is not loaded */
  public abstract DLayer getLayer(int num);

  /** returns the number of screen layers */
  public abstract int getNumLayers();

  // **************************** INHERITED METHODS *************************
  // ----------------------- org.konelabs.lang.IPlayClient ------------------
  /**
   * When IScreen.start is called the screen begins; returns whether start was
   * successful
   */
  public boolean start() {
    if (!isInitialized()) {
      return false;
    }

    if (isInitialized() && !this.isRunning) {
      this.isRunning = true;
      Thread t = new Thread(this);
      t.start();
    }

    return true;
  } // start ()

  /** stops the screen after rendering the current frame */
  public void stop() {
    this.isRunning = false;
  } // stop ()

  /**
   * states whether the screen is initialised; if not then the screen is not
   * likely to start
   */
  public boolean isInitialized() {
    return true;
  } // isInitialized ()

  /**
   * Informs the Screen to cease to exist, system may exit before destroy
   * completes, but in implementations where the program will not System.exit(0)
   * then it is important that destroy() will close any resources and stop any
   * threads.
   */
  public void destroy() {
    // TODO: Add implementation
  } // destroy ()

}
