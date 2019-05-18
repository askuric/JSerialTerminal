package serial.visual;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortList;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.text.DefaultCaret;

@SuppressWarnings("serial")
/**
 * Application enabling simple and intuitive handling serial communication. 
 * Primary intention of such design is the simplicity of saving serial data to the files for further use. 
 * It makes it very easy to make simple editions and save data to files as csv and use it further with Matlab, Excel and similar 
 */
public class JSerialTerminal extends JFrame {

	// user interaction items
	private JTextArea editor;
	private Path openFilePath;
	private JComboBox<String> ports;
	private JComboBox<Integer> baudrate;
	private JComboBox<Item> termination;
	private JTextField sendMessage;
	
	// shared variables
	private String terminationSign;
	private SerialPort serialPort;
	protected Integer activeBaudrate;

	/**
	 * Constructor method of the JSerialTerm class
	 * receives no arguments
	 * opens the application window 1000x600
	 */
	public JSerialTerminal() {
		setTitle("JSerialTerm");
		// icon adding
		setIconImage(new ImageIcon(JSerialTerminal.class.getResource("/icon-512.png")).getImage());
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		// size to open with
		setSize(1000, 600);
		// Initialise user interface
		initGUi();
		try {
			// use windows theme
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			stackTraceToEditor(e);
		}
		// on close listener add
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				int confirmed = JOptionPane.showConfirmDialog(null, "Are you sure you want to exit the program?",
						"Exit Program Message Box", JOptionPane.YES_NO_OPTION);

				if (confirmed == JOptionPane.YES_OPTION) {
					if (serialPort != null && serialPort.isOpened()) {
						try {
							serialPort.closePort();
						} catch (Exception e1) {
							stackTraceToEditor(e1);
						}
					}
					dispose();
				} else {
					setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);// cancel
				}
			}
		});
	}

	/**
	 * Method initializing the visual interface
	 */
	private void initGUi() {
		// defining layout
		getContentPane().setLayout(new BorderLayout());

		// editon definition
		editor = new JTextArea();
		DefaultCaret caret = (DefaultCaret) editor.getCaret();
		caret.setUpdatePolicy(DefaultCaret.OUT_BOTTOM);
		getContentPane().add(new JScrollPane(editor), BorderLayout.CENTER);
		
		// adding definitions of actions, menus and toolbars
		createActions();
		createMenus();
		createToolbars();

	}

	/**
	 * Method creating top and bottom toolbar
	 */
	private void createToolbars() {
		// top toolbar definition
 		JToolBar toolBarTop = new JToolBar();
		toolBarTop.setFloatable(true);
		toolBarTop.setName("Tools");
		
		// bottom toolbqr definition
		JToolBar toolBarBottom = new JToolBar();
		toolBarBottom.setFloatable(true);
		toolBarBottom.setName("Send Tools");

		// top toolbar item adding
		// save button
		toolBarTop.add(new JButton(saveDocumentAction));
		// clear editon button
		toolBarTop.add(new JButton(clearEditor));
		toolBarTop.addSeparator(new Dimension(40,20));
		// discover devices button
		toolBarTop.add(new JButton(discoverDevices));
		// initial search for available devices ports:
		Vector<String> portsAvailable = new Vector<String>();
		for (String port : SerialPortList.getPortNames()) {
			portsAvailable.addElement(port);
		}
		// dropdown with available ports
		ports = new JComboBox<String>(portsAvailable);
		ports.setAction(serialPortSelect);
		toolBarTop.add(ports);
		// dropdown with available baudrates
		Vector<Integer> baudrateAvailable = new Vector<Integer>();
		baudrateAvailable.add(1200);
		baudrateAvailable.add(2400);
		baudrateAvailable.add(4800);
		baudrateAvailable.add(9600);
		baudrateAvailable.add(19200);
		baudrateAvailable.add(38400);
		baudrateAvailable.add(57600);
		baudrateAvailable.add(115200);
		baudrateAvailable.add(230400);
		baudrateAvailable.add(460800);
		baudrateAvailable.add(921600);
		baudrateAvailable.add(1382400);
		baudrate = new JComboBox<Integer>(baudrateAvailable);
		// dropdown listener and handler
		baudrate.addActionListener(e->{
			activeBaudrate = baudrate.getItemAt(baudrate.getSelectedIndex());
		});
		toolBarTop.add(baudrate);
		toolBarTop.addSeparator();
		// open serial port button
		toolBarTop.add(new JButton(openSerial));
		// close serial port button
		toolBarTop.add(new JButton(closeSerial));
		
		// bottom toolbar item adding
		// termination character dropdown
		Vector<Item> terminationAvailable = new Vector<Item>();
		terminationAvailable.add(new Item("No line ending",""));
		terminationAvailable.add(new Item("Newline","\n"));
		terminationAvailable.add(new Item("Carriage retun","\r"));
		terminationAvailable.add(new Item("Both LN&CR","\r\n"));
		termination = new JComboBox<Item>(terminationAvailable);
		// dropdown listener
		termination.addActionListener(e->{
			terminationSign = ((Item) termination.getSelectedItem()).value();
		});
		toolBarBottom.add(termination);
		// send message textbox
		sendMessage = new JTextField();
		toolBarBottom.add(sendMessage);
		// send message button
		toolBarBottom.add(new JButton(sendSerial));

		// add toolbars
		getContentPane().add(toolBarTop, BorderLayout.PAGE_START);
		getContentPane().add(toolBarBottom, BorderLayout.PAGE_END);
	}

	/**
	 * Method creating menus of the application
	 */
	private void createMenus() {
		// main menu bar
		JMenuBar menuBar = new JMenuBar();
		// File
		JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);
		fileMenu.add(new JMenuItem(saveDocumentAction));
		fileMenu.add(new JMenuItem(saveDocumentAsAction));
		fileMenu.addSeparator();
		fileMenu.add(new JMenuItem(exitAction));
		// Edit 
		JMenu editMenu = new JMenu("Edit");
		// Settings
		JMenu settingsMenu = new JMenu("Settings");
		menuBar.add(settingsMenu);
		setJMenuBar(menuBar);

	}

	/**
	 * Method updating action descriptions for each action handler
	 */
	private void createActions() {

		// action definitions
		sendSerial.putValue(Action.NAME, "Send");
		
		closeSerial.putValue(Action.NAME, "Close Serial");
		closeSerial.putValue(Action.SHORT_DESCRIPTION, "Close opened serial port");
		
		clearEditor.putValue(Action.NAME, "Clear Editor");

		openSerial.putValue(Action.NAME, "Start Serial");
		openSerial.putValue(Action.SHORT_DESCRIPTION, "Start serial port");

		discoverDevices.putValue(Action.NAME, "Discover Devices");
		discoverDevices.putValue(Action.SHORT_DESCRIPTION, "Refresh serial ports count");
		discoverDevices.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("crtl r"));
		discoverDevices.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_R);

		saveDocumentAction.putValue(Action.NAME, "Save Data");
		saveDocumentAction.putValue(Action.SHORT_DESCRIPTION, "Used to open existing document.");
		saveDocumentAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("crtl s"));
		saveDocumentAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_S);

		saveDocumentAsAction.putValue(Action.NAME, "Save As");
		saveDocumentAsAction.putValue(Action.SHORT_DESCRIPTION, "Save data to a new location.");

		exitAction.putValue(Action.NAME, "Exit");
	}

	/**
	 * Main function just creates the visual object  
	 * 
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			new JSerialTerminal().setVisible(true);
		});
	}


	/**
	 * Serial port selected action, handles new port selected request
	 */
	private Action serialPortSelect = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			// if port opened close it first
			if (serialPort != null && serialPort.isOpened()) {
				try {
					// close port
					serialPort.closePort();
				} catch (Exception e1) {
					//port not closed for some reason
					stackTraceToEditor(e1);
					return;
				}
			}
			// when no port opened any more
			try {
				// open new port 
				serialPort = new SerialPort(ports.getItemAt(ports.getSelectedIndex()));
			} catch (Exception e1) {
				//port not opened for some reason
				stackTraceToEditor(e1);
				return;
			}
		}
	};


	/**
	 * Discover devices action class handling search for available devices request
	 */
	private Action discoverDevices = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			// remove previously found ports
			ports.removeAllItems();
			// find all available devices
			for (String port : SerialPortList.getPortNames()) {
				ports.addItem(port);
			}
		}
	};
	
	/**
	 * Send action handling sending data over serial request
	 */
	private Action sendSerial = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				// try sending data over serial port
				serialPort.writeBytes((sendMessage.getText() + terminationSign).getBytes());
			} catch (Exception e1) {
				// no data sent, dump the reason in the editor
				stackTraceToEditor(e1);
			} 
		}
	};

	/**
	 * Open serial port action class handling open serial port request
	 */
	private Action openSerial = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				// if no serial port selected yet 
				if (serialPort == null) {
					// get the item currently selected by the dropdown
					serialPort = new SerialPort(ports.getItemAt(ports.getSelectedIndex()));
				}
				// if no boudrate selected by user
				if (activeBaudrate == null) {
					// take the one currently selected by the dropdown
					activeBaudrate = baudrate.getItemAt(baudrate.getSelectedIndex());
				}
				// open the port
				serialPort.openPort();
				serialPort.setParams(activeBaudrate, 8, 1, 0);// Set params.
				// display the success
				editor.append("Port " + ports.getItemAt(ports.getSelectedIndex()) + " Opened!");
				// add the port listener
				serialPort.addEventListener(new SerialPortEventListener() {
					@Override
					public void serialEvent(SerialPortEvent arg0) {
						try {
							// read bytes when available
							byte[] buffer = serialPort.readBytes(arg0.getEventValue());
							String bufferString = new String(buffer);
							// editor display
							editor.append(bufferString);
						} catch (Exception e) {
							// serial communication error
							stackTraceToEditor(e);
						}
					}
				});
			} catch (Exception e1) {
				// port oppening error
				stackTraceToEditor(e1);
			}
		}
	};

	/**
	 * Close serial action class handling serial closing user request 
	 */
	private Action closeSerial = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				// if port opened close it
				if (serialPort != null && serialPort.isOpened()) {
					serialPort.closePort();
					// display success
					editor.append("Port " + ports.getItemAt(ports.getSelectedIndex()) + " Closed!");
				}
			} catch (Exception e1) {
				// error while closing
				stackTraceToEditor(e1);
			}
		}
	};

	
	/**
	 * Clear editor class handling editor clear request
	 */
	private Action clearEditor = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				// discard all the data from the editor
				editor.getDocument().remove(0, editor.getDocument().getLength());
			} catch (Exception e1) {
				// error while discarding
				stackTraceToEditor(e1);
			}
		}
	};

	/**
	 * Save document handling class handling save data user request
	 */
	private Action saveDocumentAction = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			// if path to the document not specified perform "Save As" behavior
			if (openFilePath == null) {
				
				// Prompt user for the file location
				JFileChooser fc = new JFileChooser();
				fc.setDialogTitle("Save Document As");
				if (fc.showSaveDialog(JSerialTerminal.this) != JFileChooser.APPROVE_OPTION) {
					// nothing chosen
					JOptionPane.showMessageDialog(JSerialTerminal.this, "Nothing has been saved!", "Message",
							JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				Path file = fc.getSelectedFile().toPath();

				if (Files.exists(file)) {
					int rez = JOptionPane.showConfirmDialog(JSerialTerminal.this,
							"Choosen file name (" + file + ") already exists. Are you sure you want to overwrite it?",
							"Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
					if (rez != JOptionPane.YES_OPTION) {
						// user does not want to overwrite document
						return;
					}

				}
				// save new path name
				openFilePath = file;
			}
			
			try {
				// write document
				Files.write(openFilePath, editor.getText().getBytes(StandardCharsets.UTF_8));
			} catch (Exception e1) {
				JOptionPane.showMessageDialog(JSerialTerminal.this,
						"Error while writing the file " + openFilePath + ": " + e1.getMessage() + ".", "Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
	};

	/**
	 * Save As document handling class handling save as data user request
	 */
	private Action saveDocumentAsAction = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			// Prompt user for the file location
			JFileChooser fc = new JFileChooser();
			fc.setDialogTitle("Save Document As");
			if (fc.showSaveDialog(JSerialTerminal.this) != JFileChooser.APPROVE_OPTION) {
				// nothing chosen
				JOptionPane.showMessageDialog(JSerialTerminal.this, "Nothing has been saved!", "Message",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			Path file = fc.getSelectedFile().toPath();

			if (Files.exists(file)) {
				int rez = JOptionPane.showConfirmDialog(JSerialTerminal.this,
						"Choosen file name (" + file + ") already exists. Are you sure you want to overwrite it?",
						"Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (rez != JOptionPane.YES_OPTION) {
					// user does not want to overwrite document
					return;
				}

			}
			// save new path name
			openFilePath = file;
			try {
				// write document
				Files.write(openFilePath, editor.getText().getBytes(StandardCharsets.UTF_8));
			} catch (Exception e1) {
				JOptionPane.showMessageDialog(JSerialTerminal.this,
						"Error while writing the file " + openFilePath + ": " + e1.getMessage() + ".", "Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
	};
	
	/**
	 * Exit action class handling exit request
	 */
	private Action exitAction = new AbstractAction() {
		@Override
		public void actionPerformed(ActionEvent e) {
			dispose();
		}
	};

	/**
	 * Utility function outputting stack trace to the editor
	 * 
	 * @param e
	 */
	public void stackTraceToEditor(Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(e.getMessage() + "\n");
		for (StackTraceElement element : e.getStackTrace()) {
			sb.append(element.toString());
			sb.append("\n");
		}
		editor.append(sb.toString());
	}
	
	/**
	 * Helping class for JComboBox for display (String) label to be different than the (String) value
	 * 
	 */
	private class Item  {
	  private String label;
	  private String value;
	  public Item(String label, String value){
	      this.label=label;
	      this.value=value;
	  }
	  /**
	   *  vqlue getter
	   * @return String value
	   */
	  public String value(){
	      return value;
	  }
	  public String toString(){
	      return label;
	  }
	}

}
