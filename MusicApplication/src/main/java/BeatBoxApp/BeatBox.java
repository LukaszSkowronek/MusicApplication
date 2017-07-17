package BeatBoxApp;


import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import org.apache.log4j.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;

public class BeatBox {

	private final Logger logger = Logger.getLogger(BeatBox.class);
	private final Map<String, Integer> instrumentToMidiCode = new TreeMap<String, Integer>()
	{{
		put("Base Drum", 35);
		put("Closed Hi-Hat", 42);
		put("Open Hi-Hat", 46);
		put("Acoustic Snare", 38);
		put("Crash Cymbal", 49);
		put("Hand Clap", 39);
		put("High Tom", 50);
		put("Hi Bongo", 60);
		put("Marcas", 70);
		put("Whistel", 72);
		put("Low Conga", 64);
		put("Cowbell", 56);
		put("Vibraslap", 58);
		put("Low-mid Tom", 47);
		put("High Agogo", 67);
		put("Open Hi Conga", 63);
	}};

	private final int NUMBER_OF_INSTRUMENTS = instrumentToMidiCode.size();
	private final int NOTE_ON = 144;
	private final int NOTE_OFF = 128;
	private final int CHANGE_INSTRUMENT = 192;
	private final int NUMBER_OF_TICKS = 16;
	private final int NUMBER_OF_CHECKBOXES = NUMBER_OF_TICKS * NUMBER_OF_TICKS;
	private int port = 4242;
	private String ip = "127.0.0.1";


	private JFrame theFrame;
	private JPanel mainPanel;
	private JList incomingList;
	private JTextField userMessage;
	private String userName;
	private ObjectInputStream in;
	private ObjectOutputStream out;

	Map<String, boolean[]> otherSeqsMap = new HashMap<String, boolean[]>();
	List<JCheckBox> checkboxList;
	Vector<String> listVector = new Vector<String>();

	Sequencer sequencer;
	Sequence mySequence = null;
	Sequence sequence;
	Track track;

	public static void main(String[] args) {
		new BeatBox().startUp(generateNick());
	}
	
	private static String generateNick(){
		String[] names = {"Andrzej", "Antek", "Eugenia", "Włodek", "Staszek"};
		String name = names[(int) (Math.random()*4)];
		return name;
		
	}

	private void startUp(String name) {
		userName = name;
		try {
			Socket sock = new Socket(ip, port);
			out = new ObjectOutputStream(sock.getOutputStream());
			in = new ObjectInputStream(sock.getInputStream());
			Thread remote = new Thread(new Runnable() {
				boolean[] checkboxState = null;
				String nameToShow = null;
				Object obj = null;

				public void run() {
					try {
						while ((obj = in.readObject()) != null) {
							logger.debug("got an object from server");
							logger.debug(obj.getClass());
							nameToShow = (String) obj;
							checkboxState = (boolean[]) in.readObject();
							otherSeqsMap.put(nameToShow, checkboxState);
							listVector.add(nameToShow);
							incomingList.setListData(listVector);
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			});
			remote.start();

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			logger.debug("Cant connect- play alone");
		}
		setUpMidi();
		buildGUI();

	}

	public void buildGUI() {
		theFrame = new JFrame("Cyber BeatBox");
		BorderLayout layout = new BorderLayout();
		JPanel background = new JPanel(layout);
		background.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		checkboxList = new ArrayList<JCheckBox>();
		Box buttonBox = new Box(BoxLayout.Y_AXIS); // vertical order

		ActionListener startAction = ActionEvent -> buildTrackAndStart();
		addButton(new JButton("Start"), startAction, buttonBox);

		ActionListener stopAction = ActionEvent -> sequencer.stop();
		addButton(new JButton("Stop"), stopAction, buttonBox);

		ActionListener speedUpAction = ActionEvent -> {
			float tempoFactor = sequencer.getTempoFactor();
			sequencer.setTempoFactor((float) (tempoFactor * 1.03));
		};
		addButton(new JButton("SpeedUp"), speedUpAction, buttonBox);

		ActionListener slowDownAction = ActionEvent -> {
			float tempoFactor = sequencer.getTempoFactor();
			sequencer.setTempoFactor((float) (tempoFactor * 0.97));
		};
		addButton(new JButton("SlowDown"), slowDownAction, buttonBox);

		ActionListener sendAction = ActionEvent -> {
			boolean[] checkboxState = new boolean[NUMBER_OF_CHECKBOXES];

			for (int i = 0; i < NUMBER_OF_CHECKBOXES; i++) {
				JCheckBox check = (JCheckBox) checkboxList.get(i);
				if (check.isSelected())
					checkboxState[i] = true;
			}

			try {
				out.writeObject(userName + ": " + userMessage.getText());
				out.writeObject(checkboxState);
			} catch (Exception ex) {
				logger.debug("can't send to the server");
			}

			userMessage.setText("");

		};
		addButton(new JButton("Send"), sendAction, buttonBox);

		userMessage = new JTextField();
		buttonBox.add(userMessage);

		incomingList = new JList();
		incomingList.addListSelectionListener((ListSelectionEvent le) -> {
			if (!le.getValueIsAdjusting()) {
				String selected = (String) incomingList.getSelectedValue();
				if (selected != null) {
					boolean[] selectedState = (boolean[]) otherSeqsMap.get(selected);
					changeSequence(selectedState);
					sequencer.stop();
					buildTrackAndStart();
				}
			}
		});
		incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane theList = new JScrollPane(incomingList);
		buttonBox.add(theList);
		incomingList.setListData(listVector);

		Box nameBox = new Box(BoxLayout.Y_AXIS); // instruments
		for (String instumentName : instrumentToMidiCode.keySet()) {
			nameBox.add(new Label(instumentName));
		}

		background.add(BorderLayout.EAST, buttonBox);
		background.add(BorderLayout.WEST, nameBox);

		theFrame.getContentPane().add(background);
		GridLayout grid = new GridLayout(NUMBER_OF_TICKS, NUMBER_OF_INSTRUMENTS);
		grid.setVgap(2);
		grid.setHgap(3);

		mainPanel = new JPanel(grid);
		background.add(BorderLayout.CENTER, mainPanel);

		for (int i = 0; i < NUMBER_OF_CHECKBOXES; i++) { // creates checkboxes
			JCheckBox c = new JCheckBox();
			c.setSelected(false);
			checkboxList.add(c);
			mainPanel.add(c);
		}
		theFrame.setBounds(50, 50, 300, 300);
		theFrame.pack();
		theFrame.setVisible(true);
		theFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	private void addButton(JButton button, ActionListener listener, Box buttonBox) {
		button.addActionListener(listener);
		buttonBox.add(button);
	}

	private void changeSequence(boolean[] checkboxState) {
		for (int i = 0; i < NUMBER_OF_CHECKBOXES; i++) {
			JCheckBox check = (JCheckBox) checkboxList.get(i);
			if (checkboxState[i]) {
				check.setSelected(true);
			} else {
				check.setSelected(false);
			}
		}
	}

	public void setUpMidi() {
		try {
			sequencer = MidiSystem.getSequencer();
			sequencer.open();
			sequence = new Sequence(Sequence.PPQ, 4);
			track = sequence.createTrack();
			sequencer.setTempoInBPM(120);

		} catch (MidiUnavailableException | InvalidMidiDataException e) {
			e.printStackTrace();
		}
	}

	public void buildTrackAndStart() {
		List<Integer> trackList = null;

		sequence.deleteTrack(track);
		track = sequence.createTrack();

		for (int i = 0; i < NUMBER_OF_TICKS; i++) {
			trackList = new ArrayList<Integer>();
			for (int j = 0; j < NUMBER_OF_INSTRUMENTS; j++) {
				JCheckBox jc = (JCheckBox) checkboxList.get(j + (NUMBER_OF_INSTRUMENTS * i));
				if (jc.isSelected()) {
					int key = (int)instrumentToMidiCode.values().toArray()[i];
					trackList.add(key);
				} else {
					trackList.add(null);
				}
			}
			makeTracks(trackList);

		}
		track.add(makeEvent(CHANGE_INSTRUMENT, 9, 1, 0, 15));
		try {
			sequencer.setSequence(sequence);
			sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY);
			sequencer.start();
			sequencer.setTempoInBPM(120);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
	}

	public void makeTracks(List<Integer> trackList) {
		Iterator<Integer> it = trackList.iterator();
		for (int i = 0; i < NUMBER_OF_TICKS; i++) {
			Integer num = (Integer) it.next();

			if (num != null) {
				int InstrumentType = num.intValue();
				track.add(makeEvent(NOTE_ON, 9, InstrumentType, 100, i));
				track.add(makeEvent(NOTE_OFF, 9, InstrumentType, 100, i + 1));
			}
		}
	}

	public MidiEvent makeEvent(int messageType, int channel, int noteToPlay, int howHardToPlayANote, int whenTrigger) { //poprawwic
		MidiEvent event = null;

		try {
			ShortMessage messageToMidi = new ShortMessage();
			messageToMidi.setMessage(messageType, channel, noteToPlay, howHardToPlayANote);
			event = new MidiEvent(messageToMidi, whenTrigger);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return event;
	}

}
