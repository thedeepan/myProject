
import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javax.sound.midi.ShortMessage.*;

public class BeatBoxFinal {

    private JList<String> incomingList;
    private JTextArea userMessage;
    private ArrayList<JCheckBox> checkboxList;

    private Vector<String> listVector = new Vector<>();
    private HashMap<String,boolean[]> otherSeqsMap = new HashMap<>();

    private String userName;
    private int nextNum;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private Sequencer sequencer;
    private Sequence sequence;
    private Track track;

    String[] instrumentNames = {
            "Bass Drum", "Closed Hi-Hat", "Open Hi-Hat","Acoustic Snare",
            "Crash Cymbal", "Hand Clap", "High Tom",
            "Hi Bongo", "Maracas", "Whistle", "Low Conga",
            "Cowbell", "Vibraslap", "Low-mid Tom", "High Agogo","Open Hi Conga"
    };
    int [] instruments ={35, 42, 46, 38, 49, 39, 50, 60, 70, 72, 64, 56, 58, 47, 67, 63};
    public static void main(String[]args){
        new BeatBoxFinal().startUp("Bass Drum");
    }
    public void startUp(String name){
        userName = name;
        //open connection to the server
        try{
            Socket socket = new Socket("127.0.0.1",4242);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                executor.submit(new RemoteReader());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        setUpMidi();
        buildGUI();
    }


    private void buildGUI() {
        JFrame frame = new JFrame("Cyber BeatBox");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        BorderLayout layout = new BorderLayout();
        JPanel background = new JPanel(layout);
        background.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        Box buttonBox = new Box(BoxLayout.Y_AXIS);
        JButton start = new JButton("Start");
        start.addActionListener(e -> buildTrackAndStart());
        buttonBox.add(start);

        JButton stop = new JButton("Stop");
        stop.addActionListener(e -> sequencer.stop());
        buttonBox.add(stop);

        JButton upTempo = new JButton("Tempo Up");
        upTempo.addActionListener(e -> changeTempo(1.03f));
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("Tempo Down");
        downTempo.addActionListener(e -> changeTempo(0.97f));
        buttonBox.add(downTempo);

        JButton sendIt = new JButton("sentIt");
        sendIt.addActionListener(e -> sendMessageAndTrack());
        buttonBox.add(sendIt);

        userMessage = new JTextArea();
        userMessage.setLineWrap(true);
        userMessage.setWrapStyleWord(true);
        JScrollPane messageScroller = new JScrollPane(userMessage);
        buttonBox.add(messageScroller);
        incomingList = new JList<>();
        incomingList.addListSelectionListener(new MyListSelectorListener());
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane theList = new JScrollPane(incomingList);
        buttonBox.add(theList);
        incomingList.setListData(listVector);

        Box nameBox = new Box(BoxLayout.Y_AXIS);
        for(String instrumentName : instrumentNames){
            JLabel instrumentLabel = new JLabel(instrumentName);
            instrumentLabel.setBorder(BorderFactory.createEmptyBorder(4,1,4,1));
            nameBox.add(instrumentLabel);
        }
        background.add(BorderLayout.EAST, buttonBox);
        background.add(BorderLayout.WEST,nameBox);

        frame.getContentPane().add(background);
        GridLayout grid = new GridLayout(16,16);
        grid.setVgap(1);
        grid.setHgap(2);

        JPanel mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER, mainPanel);

        checkboxList = new ArrayList<>();
        for(int i = 0; i<256;i++){
            JCheckBox c = new JCheckBox();
            c.setSelected(true);
            checkboxList.add(c);
            mainPanel.add(c);
        }
        frame.setBounds(50,50,300,300);
        frame.pack();
        frame.setVisible(true);
    }


    private void setUpMidi() {
        try{
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequence = new Sequence(Sequence.PPQ,4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        } catch (MidiUnavailableException | InvalidMidiDataException e) {
            throw new RuntimeException(e);
        }

    }
    private void buildTrackAndStart() {
        ArrayList<Integer> trackList;// this will hod the instruments for each
        sequence.deleteTrack(track);
        track = sequence.createTrack();
        for(int i =0;i<16;i++){
            trackList= new ArrayList<>();
            int key = instruments[i];
            for(int j = 0; j<16;j++){
                JCheckBox jc = checkboxList.get(j+(16*i));
                if(jc.isSelected()){
                    trackList.add(key);
                }
                else {
                    trackList.add(null);// Because this slot should be empty in the tarck
                }
            }
            makeTracks(trackList);
            track.add(makeEvent(CONTROL_CHANGE, 1,127,0,16));
        }
        track.add(makeEvent(PROGRAM_CHANGE,9,1,0,15));
        try{
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY);
            sequencer.setTempoInBPM(120);
            sequencer.start();
        } catch (InvalidMidiDataException e) {
            throw new RuntimeException(e);
        }

    }



    private void changeTempo(float tempoMultiplier) {
        float tempoFactor = sequencer.getTempoFactor();
        sequencer.setTempoFactor(tempoFactor * tempoMultiplier);
    }
    private void sendMessageAndTrack() {
        boolean [] checkboxState = new boolean[256];
        for(int i = 0;i<256;i++){
            JCheckBox check = checkboxList.get(i);
            if(check.isSelected()){
                checkboxState[i] =true;
            }
        }
        try{
            out.writeObject(userName + nextNum++ + ": "+  userMessage.getText());
            out.writeObject(checkboxState);
        } catch (IOException e) {
            System.out.println("Terribly sorry. Could not send  it to the server"+e.getMessage());
            throw new RuntimeException(e);
        }

        userMessage.setText("");
    }
    public class MyListSelectorListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent lse){
            if(!lse.getValueIsAdjusting()){
                String selected = incomingList.getSelectedValue();
                if(selected != null){
                    //now go to the map, and change the sequence
                    boolean [] selectedState = otherSeqsMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                }
            }
        }
    }
    private void changeSequence (boolean[] checkboxState){
        for(int i = 0;i<256;i++){
            JCheckBox check = checkboxList.get(i);
            check.setSelected(checkboxState[i]);
        }
    }
    private void makeTracks(ArrayList<Integer> list) {
        for(int i = 0 ; i<list.size();i++){
            Integer instrumentKey =list.get(i);
            if(instrumentKey != null){
                track.add(makeEvent(NOTE_ON,9, instrumentKey,100,i));
                track.add(makeEvent(NOTE_OFF,9,instrumentKey,100,i+1));
            }
        }
    }

    public static MidiEvent makeEvent(int cmd, int chnl, int one, int two, int tick) {
        MidiEvent event = null;
        try{
            ShortMessage msg = new ShortMessage();
            msg.setMessage(cmd,chnl,one, two);
            event = new MidiEvent(msg, tick);
        } catch (InvalidMidiDataException e) {
            throw new RuntimeException(e);
        }
        return event;
    }

    private class RemoteReader implements Runnable {
        public void run(){
            try{
                Object obj;
                while((obj = in.readObject()) !=null){
                    System.out.println("got an object from server");
                    System.out.println(obj.getClass());

                    String nameToShow = (String) obj;
                    boolean[] checkboxState = (boolean[]) in.readObject();
                    otherSeqsMap.put(nameToShow,checkboxState);

                    listVector.add(nameToShow);
                    incomingList.setListData(listVector);
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

        }
    }
}
