package TicTacToeFinalProject;

//CSCI 4300/5300 MWF 10:00 - 10:50
//Binqi Sun
//Operation Systems Project 2
//Create an Internet Game

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;

//Create a TicTT game class
public class TicTT extends Thread {

	private static boolean Ready = false;
	private String Detail[];

	public TicTT(String Type) throws Exception {
		super(Type);
	}

	
	public TicTT(String Type, String IP) throws Exception {
		int Games = 0;
		String User = null;
		Detail = new String[1000];
		for (int i = 0; i < 1000; i++)
			Detail[i] = null;

		if (Type.toUpperCase().equals("CLIENT")) {
			try {
				while (true) {
					Client ClientGame = new Client(IP, Detail, User);
					ClientGame.Play();

					//play the game again.
					if (!ClientGame.stats.getText().equals("Server Stopped")) {
						Detail[++Games] = ClientGame.GetGameResult();
						User = ClientGame.GetName();

						if (ClientGame.Again())
							ClientGame.dispose();
						else
							break;
					} else
						break;
				}
			} catch (Exception t) {
				System.err.println("Exception when connecting to server: " + t.toString());
			}
		}
	}
	//run the server, also run the client threads
	public void run() {
		if (Thread.currentThread().getName().equals("ServerThread")) {
			if (!Ready) {
				try {
					Ready = true;
						Server ServerGame = new Server();
				} catch (Exception t) {
					System.err.println("Exception when starting server: " + t.toString());
				}
			}
		} else if (Thread.currentThread().getName().equals("ClientThread")) {
			int Games = 0;
			String User = null;

			Detail = new String[1000];
			for (int i = 0; i < 1000; i++)
				Detail[i] = null;
			try {

				while (!Ready)
					sleep(100);
				while (true) {
					Client ClientGame = new Client("192.168.1.3", Detail, User);
					ClientGame.Play();
					// game results
					Detail[++Games] = ClientGame.GetGameResult();
					User = ClientGame.GetName();

					//play the game again
					if (!ClientGame.stats.getText().equals("Server Stopped.")) {
						if (ClientGame.Again())
							ClientGame.dispose();
						else
							break;
					} else
						break;
				}
			} catch (Exception t) {
				System.err.println("Exception when connecting to server: " + t.toString());
			}
		}
	}

	//  Game server
	private class Server {
		//declare the server socket and client socket
		private Game TTT = new Game();
		private ServerSocket Ssocket = null;
		private Socket Csocket = null;	

		public Server() throws Exception {
			// setup serversocket
			try {
				Ssocket = new ServerSocket(8888);

			} catch (IOException t) {
				System.err.println("Server could not find the port");
				System.exit(1);
			}
			try {
				while (true) {
					//Add player to the server and client
					TTT.AddP(new Handler(TTT, 'x', listen()));
					TTT.AddP(new Handler(TTT, 'o', listen()));
					if (TTT.Full()) {
						TTT.Setcompetitor();
						TTT.Player1().start();
						TTT.Player2().start();
						// start a new game
						TTT = new Game();
					}
				}
			} finally {
				try {
					//close the server socket
					Ssocket.close();
				} catch (IOException t) {
					System.out.println("Error on closing socket.");
				}
			}
		}

		public Socket listen() {
			Socket Temp = null;

			// start serversocket to listen
			try {
				System.out.println("Server is listening");

				Temp = Ssocket.accept();
			} catch (IOException t) {
				System.err.println("Error on server listening");
				System.exit(1);
			}
			return Temp;
		}

		//The class handle the game
		private class Game {
			private Handler Handler1 = null;
			private Handler Handler2 = null;

			private Handler MyHandler = null;
			private MyGui Gui[];

			//declare variables for the correct and wrong moves
			public static final int correct = 1;
			public static final int wrong = 2;
			public static final int stop = 3;

			private int none = 0;

			public Game() {
				Gui = new MyGui[9];
				for (int i = 0; i < 9; i++)
					Gui[i] = new MyGui();
			}
			//add new player to the handler
			public void AddP(Handler NewPlayer) {
				if (Handler1 == null)
					Handler1 = NewPlayer;
				else
					Handler2 = NewPlayer;
			}

			public void Setcompetitor() {
				Handler1.Setcompetitor(Handler2);
				Handler2.Setcompetitor(Handler1);
			}

			public boolean Full() {
				return ((Handler1 != null) && (Handler2 != null));
			}

			public Handler Player1() {
				return Handler1;
			}

			public Handler Player2() {
				return Handler2;
			}

			//verify the moves 
			public synchronized int verifyMoves(Handler Player, int spot) {
				int Value = wrong;
				try {
					if (Player == MyHandler) {
						// if nobody choose that spot
						if (Gui[spot].Player == null) {
							// assign Gui spot and move order
							Gui[spot].Player = Player;
							Gui[spot].MoveOrder = none++;
							// set new current player
							MyHandler = Player.Getcompetitor();
							MyHandler.CompetitorMov(spot);
							Value = correct;
						} else
							Value = wrong;
					} else
						//same player can't move twice in a row.
						Value = stop;
				} catch (Exception t) {
					System.out.println("Error on verifying the moves.");
				}
				return Value;
			}

			// verify enter name
			public synchronized boolean verifyName(Handler Player, String InputName) {
				if (Player.Getcompetitor().GetName() == null || !Player.Getcompetitor().GetName().equals(InputName)) {
					Player.SetName(InputName);
					Player.Getcompetitor().CompetitorName(InputName);
					return true;
				} else
					return false;
			}

			// send a move back request to the Competitor
			public synchronized void verifyMoveBack(Handler Player) {
				Player.Getcompetitor().CompMoveBack();
			}

			public synchronized void AMoveBack(String Result, Handler ResponsePlayer) {
				int LastMove1, LastMove2;

				if (Result.equals("YES")) {
					if (MyHandler == ResponsePlayer.Getcompetitor()) {
						
						// remove circle's last move on server side
						LastMove2 = GetLastMove(MyHandler.Getcompetitor());
						RemoveMove(LastMove2);

						// remove cross's last move on server side
						LastMove1 = GetLastMove(MyHandler);
						RemoveMove(LastMove1);
						// modify and remove the circle and cross on client side 
						MyHandler.Getcompetitor().MoveBack(ResponsePlayer.Getcompetitor().GetName(), LastMove2);
						MyHandler.Getcompetitor().MoveBack(ResponsePlayer.Getcompetitor().GetName(), LastMove1);
						MyHandler.MoveBack(ResponsePlayer.Getcompetitor().GetName(), LastMove2);
						MyHandler.MoveBack(ResponsePlayer.Getcompetitor().GetName(), LastMove1);

					
					} else {
						// modify server Gui remove cross's last move
						LastMove1 = GetLastMove(MyHandler.Getcompetitor());
						RemoveMove(LastMove1);
						// modify client's Gui return the name
						MyHandler.Getcompetitor().MoveBack(ResponsePlayer.Getcompetitor().GetName(), LastMove1);
						MyHandler.MoveBack(ResponsePlayer.Getcompetitor().GetName(), LastMove1);
						// current player change back to X
						SetMyHandler2(MyHandler.Getcompetitor());
					}
				} else {
					MyHandler.MoveBack(ResponsePlayer.Getcompetitor().GetName(), -1);
					MyHandler.Getcompetitor().MoveBack(ResponsePlayer.Getcompetitor().GetName(), -1);
				}
			}

			// delete a move from the game Gui
			public void RemoveMove(int spot) {
				Gui[spot].Player = null;
				Gui[spot].MoveOrder = -1;
				none--;
			}

			// get the last move 
			public int GetLastMove(Handler Player) {
				int MaxMove = -1;
				int LastMove = -1;
				
				//only 9 spot on the Gui
				for (int i = 0; i < 9; i++) {
					if (Gui[i].Player == Player) {
						if (Gui[i].MoveOrder > MaxMove) {
							MaxMove = Gui[i].MoveOrder;
							LastMove = i;
						}
					}
				}
				return LastMove;
			}

			public Handler GetMyHandler() {
				return MyHandler;
			}

			public synchronized void SetMyHandler(Handler Player) {
				if (MyHandler == null)
					MyHandler = Player;
			}
			//request move back one step
			public void SetMyHandler2(Handler Player) {
				MyHandler = Player;
			}

			//the class check the winner
			public boolean Winner() {
				return (((Gui[0].Player != null) && (Gui[0].Player == Gui[1].Player)
						&& (Gui[1].Player == Gui[2].Player))
						|| ((Gui[3].Player != null) && (Gui[3].Player == Gui[4].Player)
								&& (Gui[4].Player == Gui[5].Player))
						|| ((Gui[6].Player != null) && (Gui[6].Player == Gui[7].Player)
								&& (Gui[7].Player == Gui[8].Player))
						|| ((Gui[0].Player != null) && (Gui[0].Player == Gui[3].Player)
								&& (Gui[3].Player == Gui[6].Player))
						|| ((Gui[1].Player != null) && (Gui[1].Player == Gui[4].Player)
								&& (Gui[4].Player == Gui[7].Player))
						|| ((Gui[2].Player != null) && (Gui[2].Player == Gui[5].Player)
								&& (Gui[5].Player == Gui[8].Player))
						|| ((Gui[0].Player != null) && (Gui[0].Player == Gui[4].Player)
								&& (Gui[4].Player == Gui[8].Player))
						|| ((Gui[2].Player != null) && (Gui[2].Player == Gui[4].Player)
								&& (Gui[4].Player == Gui[6].Player)));
			}

			// check whether game is tie or not
			public boolean Tie() {
				boolean GuiF = true;
				for (int i = 0; i < 9; i++) {
					if (Gui[i].Player == null)
						GuiF = false;
				}
				// if Gui is full but still no winner, then tie
				if ((GuiF) && (Winner() == false))
					return true;
				else
					return false;
			}
		}

		private class MyGui {
			public Handler Player = null;
			public int MoveOrder = -1;
		}

		private class Handler extends Thread {
			private Handler Competitor;
			private PrintWriter message = null;
			private BufferedReader input = null;
			private Socket socket;

			private Game TTT;
			private char image;
			private String TheName;
			private boolean End = false;

			public Handler(Game gm, char tempsign, Socket Csocket) throws Exception {
				TTT = gm;
				image = tempsign;
				try {
					// client connects and we setup clientsocket to communicate
					// with client
					socket = Csocket;
					message = new PrintWriter(Csocket.getOutputStream(), true);
					input = new BufferedReader(new InputStreamReader(Csocket.getInputStream()));
					// first Reply to client after first connect
					message.println("Welcome");
					message.println("YRSIGN " + image);
				} catch (IOException t) {
					System.out.println("Unable to start the game.");
				}
			}

			public Handler Getcompetitor() {
				return Competitor;
			}

			public void Setcompetitor(Handler NewPlayer) {
				Competitor = NewPlayer;
				message.println("COMPET");
			}

			// the class start to listen
			public void run() {
				String Instruction = null;
				String Code = null;
				String Pvalue = null;
				try {
					while (!End) {
						Instruction = input.readLine();
						if (Instruction != null) {
							Code = Instruction.substring(0, 6);
							Pvalue = Instruction.substring(6).trim();
						}
						if (Code.equals("MOVETO")) {
							// the player who move first will be set as current player
							if (TTT.GetMyHandler() == null)
								TTT.SetMyHandler(this);
							//verify the move when player moves
							if (TTT.verifyMoves(this, Integer.parseInt(Pvalue)) == TTT.correct) {
								message.println("MOVEOK " + Pvalue);
								// show the winner
								if (TTT.Winner())
									message.println("YOUWIN");
								//show the game tied
								else if (TTT.Tie())
									message.println("GAMTIE");
							} else if (TTT.verifyMoves(this, Integer.parseInt(Pvalue)) == TTT.wrong)
								message.println("MOVENK");
							else if (TTT.verifyMoves(this, Integer.parseInt(Pvalue)) == TTT.stop)
								message.println("NOTURT");
							
						} else if (Code.equals("INNAME")) {
							//verify the name when player input the name
							if (TTT.verifyName(this, Pvalue) == true)
								message.println("NAMEOK " + Pvalue);
							else
								message.println("NAMENK");
						}
						// request move back
						else if (Code.equals("MOVEBK")) {
							TTT.verifyMoveBack(this);
						}
						// Reply to move back request
						else if (Code.equals("REQRES")) {
							TTT.AMoveBack(Pvalue, this);
						}
						// show message the player quit
						else if (Code.equals("GMQUIT")) {
							System.out.println("Player disconnected ");
							End = true;
						}
					}
				} catch (IOException t) {
					System.out.println("Player disconnected ");
					Competitor.CompetitorEnd();
				} finally {
					try {
						//close the socket
						socket.close();
					} catch (IOException t) {
						System.out.println("Error when closing playerhelp socket.");
					}
				}
			}

			public void CompetitorEnd() {
				message.println("COMPEND");
			}

			public void CompetitorMov(int Loc) {
				// call this player to record Competitor move
				message.println("OPPMOV " + Loc);
				//show message competitor lose or tie the game
				if (TTT.Winner())
					message.println("YOULSE");
				else if (TTT.Tie())
					message.println("GAMTIE");
			}
			//get the input name
			public String GetName() {
				return TheName;
			}

			public void SetName(String InputName) {

				TheName = InputName;

			}
			
			//set the name for competitor
			public void CompetitorName(String InputName) {
				message.println("OPPNAM " + InputName);
			}

			// competitors request to move back
			public void CompMoveBack() {
				message.println("OPPREQ");
			}

			public void MoveBack(String applicant, int spot) {
				if (spot == -1)
					// not authorize
					message.println("REQRES NK " + "0" + applicant);
				else
					message.println("REQRES OK " + spot + applicant);
			}
		}
	}

	// class Client
	private class Client extends JFrame {
		//declare all variables for JFrame
		private JPanel TTTButtons;
		//button for the game blanks fill the x and o
		private JButton blanks[];
		//show status on the bottom
		private JLabel stats;
		private JPanel TTTInput;
		private JLabel TTTNames;
		private JTextField txtName;
		private JButton enter;
		private JLabel TTTSignMsg;
		private JLabel TTTSign;
		//panel for buttons
		private JPanel BtnPanel;
		//Begin, previous, next,end, moveback, detail buttons
		private JButton BBegin, BPrev, BNext, BEnd;
		private JPanel Others;
		private JButton BMoveB;
		private JButton BDetail;
		//set a panel for Detail info
		private JPanel DetailPanel;
		
		// set a panel for showing other info
		private JPanel InfoPanel;
		private JLabel TheName;
		private JLabel ComptName;
		private ImageIcon TurnIcon;
		private JLabel Turn;
		private JLabel ComptTurn;
		private JPanel RightPanel;
		private JPanel BottomPanel;
		private Socket TTTSocket = null;
		private PrintWriter message = null;
		private BufferedReader input = null;
		private boolean End = false;
		private boolean Start = false;
		private boolean ComptArrived = false;
		private String image;
		private ImageIcon Icon;
		private ImageIcon ComptIcon;
		private String PName = null;
		private String CName = null;
		private String Old[] = new String[9];
		private int none = 0;
		private int View = -1;

		private String OutCome = null;
		private String Detail[];

		// constructor
		public Client(String IP, String in_stat[], String User) {
			super("Client");

			Detail = new String[1000];
			Detail = in_stat;

			addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent t) {
					System.exit(0);
				}
			});
			// setup the socket
			try {
				TTTSocket = new Socket(IP, 8888);
				message = new PrintWriter(TTTSocket.getOutputStream(), true);
				input = new BufferedReader(new InputStreamReader(TTTSocket.getInputStream()));
			} catch (UnknownHostException t) {
				System.err.println("Unknown IP Address:" + IP);
				System.exit(1);
			} catch (IOException t) {
				System.err.println("IP Address " + IP + " has no Reply");
				System.exit(1);
			}


			Container c = getContentPane();
			c.setLayout(new BorderLayout());

			// create a button panel to hold 9 blanks
			TTTButtons = new JPanel();
			TTTButtons.setLayout(new GridLayout(3, 3, 0, 0));
			blanks = new JButton[9];

			// initialize 9 blanks
			for (int i = 0; i < blanks.length; i++) {
				final int j = i;
				blanks[i] = new JButton("");

				blanks[i].addMouseListener(new MouseAdapter() {
					public void mouseReleased(MouseEvent t) {

						if (Start && !End) {
							message.println("MOVETO " + j);
						}
					}
				});

				TTTButtons.add(blanks[i]);
			}

			TTTInput = new JPanel();
			TTTInput.setLayout(new BoxLayout(TTTInput, BoxLayout.Y_AXIS));
			TTTNames = new JLabel("Player Name: ");
			txtName = new JTextField(10);
			if (User != null)
				txtName.setText(User);
			TTTSignMsg = new JLabel("Your image is:");
			TTTSign = new JLabel(" ");
			TTTSign.setBorder(BorderFactory.createEtchedBorder());
			enter = new JButton("enter");
			enter.addMouseListener(new MouseAdapter() {
	public void mouseReleased(MouseEvent t) {
					if (ComptArrived) {
						if (txtName.getText().trim().length() > 0) {
							// send your name to the server to check
							message.println("INNAME " + txtName.getText());
						} else
							ShowErrMessageDialog("Please enter your name",
									"Error Message");
					} else
						ShowErrMessageDialog("Please wait your competitor", "Error Message");

				}
			});
			TTTInput.add(TTTNames);
			TTTInput.add(Box.createRigidArea(new Dimension(0, 5)));
			TTTInput.add(txtName);
			TTTInput.add(enter);
			TTTInput.add(Box.createRigidArea(new Dimension(0, 30)));
			TTTInput.add(TTTSignMsg);
			TTTInput.add(Box.createRigidArea(new Dimension(0, 5)));
			TTTInput.add(TTTSign);
			RightPanel = new JPanel();
			RightPanel.add(TTTInput);

			// bottom of button panel
			BottomPanel = new JPanel();
			BottomPanel.setLayout(new BoxLayout(BottomPanel, BoxLayout.Y_AXIS));
			InfoPanel = new JPanel();
			InfoPanel.setLayout(new BoxLayout(InfoPanel, BoxLayout.X_AXIS));
			TheName = new JLabel(" ");
			ComptName = new JLabel(" ");
			Turn = new JLabel(" ");
			ComptTurn = new JLabel(" ");
			TurnIcon = new ImageIcon("reddot1.gif");
			InfoPanel.add(Box.createRigidArea(new Dimension(140, 0)));
			InfoPanel.add(Turn);
			InfoPanel.add(Box.createRigidArea(new Dimension(10, 0)));
			InfoPanel.add(TheName);
			InfoPanel.add(new JLabel("   VS   "));
			InfoPanel.add(ComptName);
			InfoPanel.add(Box.createRigidArea(new Dimension(10, 0)));
			InfoPanel.add(ComptTurn);
			InfoPanel.add(Box.createHorizontalGlue());

			//create panel and buttons for begin, previous, next, and end
			BtnPanel = new JPanel();
			BtnPanel.setLayout(new BoxLayout(BtnPanel, BoxLayout.X_AXIS));
			BBegin = new JButton("BEGIN");
			BPrev = new JButton("PREVIOUS");
			BNext = new JButton("NEXT");
			BEnd = new JButton("END");
			DirectionListener directionListener = new DirectionListener();
			BBegin.addActionListener(directionListener);
			BPrev.addActionListener(directionListener);
			BNext.addActionListener(directionListener);
			BEnd.addActionListener(directionListener);
			
			//set the panel for begin, previous, next, and end
			BtnPanel.add(Box.createRigidArea(new Dimension(65, 0)));
			BtnPanel.add(BBegin);
			BtnPanel.add(Box.createRigidArea(new Dimension(5, 0)));
			BtnPanel.add(BPrev);
			BtnPanel.add(Box.createRigidArea(new Dimension(5, 0)));
			BtnPanel.add(BNext);
			BtnPanel.add(Box.createRigidArea(new Dimension(5, 0)));
			BtnPanel.add(BEnd);
			BtnPanel.add(Box.createHorizontalGlue());

			Others = new JPanel();
			Others.setLayout(new BoxLayout(Others, BoxLayout.X_AXIS));
			BMoveB = new JButton("Undo");
			BDetail = new JButton("Detail");
			BMoveB.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent t) {

					// request if not blank
					if (Moved() && !End) {
						if (AcceptReq() == true) {
							// request to take back 1 move
							message.println("MOVEBK");
							stats.setText("You sent a move back request");
						} else
							stats.setText("The request is cancelled");
					} else {
						if (!End)
							ShowErrMessageDialog("Please make your move first",
									"Error Message");
					}
				}
			});

			BDetail.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent t) {
					float TieN = 0;
					float WinN = 0;
					float LoseN = 0;
					float Total = 0;
					float TieP = 0;
					float WinP = 0;
					float LoseP = 0;
					String Output = null;

					for (int i = 1; i < Detail.length && Detail[i] != null; i++) {
						if (Detail[i].equals("WIN"))
							WinN++;
						else if (Detail[i].equals("LOS"))
							LoseN++;
						else if (Detail[i].equals("TIE"))
							TieN++;
					}

					Total = WinN + LoseN + TieN;

					if (Total > 0) {
						WinP = (WinN / Total) * 100;
						LoseP = (LoseN / Total) * 100;
						TieP = (TieN / Total) * 100;
					}

					Output = "Game Detail: \n" + "Total number of games played: " + (int) Total
							+ "\nNumber of games WIN: " + (int) WinN + "\nNumber of games LOSE: " + (int) LoseN
							+ "\nNumber of games TIE: " + (int) TieN + "\n" + "\n Average winning percentage is: "
							+ WinP + "%" + "\n Average losing percentage is: " + LoseP + "%"
							+ "\n Average tieing percentage is: " + TieP + "%";
					ShowStatDialog(Output);
				}
			});

			Others.add(Box.createRigidArea(new Dimension(100, 0)));
			Others.add(BMoveB);
			Others.add(BDetail);
			Others.add(Box.createHorizontalGlue());

			DetailPanel = new JPanel();
			DetailPanel.setLayout(new BoxLayout(DetailPanel, BoxLayout.X_AXIS));
			stats = new JLabel("Status:");
			DetailPanel.add(stats);
			DetailPanel.add(Box.createHorizontalGlue());

			BottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
			BottomPanel.add(InfoPanel);
			BottomPanel.add(Box.createRigidArea(new Dimension(0, 20)));
			BottomPanel.add(BtnPanel);
			BottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
			BottomPanel.add(Others);
			BottomPanel.add(DetailPanel);

			// add all components to the content pane
			c.add(TTTButtons);
			c.add(BottomPanel, BorderLayout.SOUTH);
			c.add(RightPanel, BorderLayout.EAST);

			setSize(650, 600);
			setVisible(true);
		}

		// client listen to playhelper's Reply
		public void Play() throws Exception {
			String ServerResponse = null;
			String Code = null;
			String Pvalue = null;
			try {
				while (!End) {
					ServerResponse = input.readLine();
					Code = ServerResponse.substring(0, 6);
					Pvalue = ServerResponse.substring(6).trim();
					//show message server player connected
					if (Code.equals("Welcome")) {
						stats.setText("CONNECTED, waiting for your Competitor...");
					}
					// Competitor has arrived
					else if (Code.equals("COMPET")) {
						stats.setText(
								"Competitor has connected, please enter your name");
						ComptArrived = true;
					}
					// set your image
					else if (Code.equals("YRSIGN")) {
						image = Pvalue;
						if (Pvalue.equals("x")) {
							Icon = new ImageIcon("circle.jpg");
							ComptIcon = new ImageIcon("cross.jpg");
						} else {
							Icon = new ImageIcon("cross.jpg");
							ComptIcon = new ImageIcon("circle.jpg");
						}
						TTTSign.setIcon(Icon);
						TTTSign.repaint();
						//show competitor name
					} else if (Code.equals("OPPNAM")) {
						CName = Pvalue;
						ComptName.setText(Pvalue);
						stats.setText("Your Competitor is " + Pvalue);

						if (PName != null)
							Start = true;
					} else if (Code.equals("NAMEOK")) {
						PName = Pvalue;
						TheName.setText(Pvalue);
						stats.setText("Status: Welcome, " + Pvalue);

						if (CName != null)
							Start = true;
						enter.setEnabled(false);
						txtName.setEnabled(false);
						//reset name if the name is used
					} else if (Code.equals("NAMENK")) {
						ShowErrMessageDialog(
								"You name is already Used",
								"Error Message");
					} else if (Code.equals("MOVEOK")) {
						stats.setText("Waiting for Competitor to move..");
						Turn.setIcon(null);
						Turn.repaint();
						ComptTurn.setIcon(TurnIcon);
						ComptTurn.repaint();

						// store my move
						Old[none++] = "MY" + Pvalue;
						View = none - 1;

						CleanAndRepaint();

					}

					else if (Code.equals("MOVENK")) {
						//players cannot click the same blank
						stats.setText("This blank is already Used by competitor");
					} else if (Code.equals("OPPMOV")) {
						stats.setText("It's your turn now");
						Turn.setIcon(TurnIcon);
						Turn.repaint();
						ComptTurn.setIcon(null);
						ComptTurn.repaint();
						// store Competitor's move
						Old[none++] = "OP" + Pvalue;
						View = none - 1;
						CleanAndRepaint();
					}
					// not your turn
					else if (Code.equals("NOTURT")) {
						stats.setText("Not your turn");
					}
					// Competitor request to move back
					else if (Code.equals("OPPREQ")) {
						int Reply = JOptionPane.showConfirmDialog(this, "Competitor requests to move back 1 step",
								"Game Message ", JOptionPane.YES_NO_OPTION);

						if (JOptionPane.YES_OPTION == Reply)
							message.println("REQRES YES");
						else
							message.println("REQRES NO");
					}
					// receive request Reply
					else if (Code.equals("REQRES")) {
						int spot;
						String applicant = null;

						spot = Integer.parseInt(Pvalue.substring(3, 4));
						applicant = Pvalue.substring(4).trim();

						// agree the request
						if (Pvalue.substring(0, 2).equals("OK")) {

							DeleteLastMove(spot);
							CleanAndRepaint();
							if (applicant.equals(PName)) {
								stats.setText("You have moved back one step, it is your turn now...");

								Turn.setIcon(TurnIcon);
								Turn.repaint();
								ComptTurn.setIcon(null);
								ComptTurn.repaint();
							} else {
								stats.setText("Accept the request, waiting for Competitor to move...");

								Turn.setIcon(null);
								Turn.repaint();
								ComptTurn.setIcon(TurnIcon);
								ComptTurn.repaint();
							}
							//reject the request
						} else {

							if (applicant.equals(PName))
								stats.setText("Your competitor reject your request");
							else
								stats.setText("The request got rejected");
						}
					} else if (Code.equals("COMPEND")) {
						stats.setText("Competitor disconnected.");
						ShowErrMessageDialog("Competitor disconnected",
								"Error Message");
						End = true;
					} else if (Code.equals("YOUWIN")) {
						stats.setText("You Win!!!");
						OutCome = "WIN";

						Turn.setIcon(null);
						Turn.repaint();
						ComptTurn.setIcon(null);
						ComptTurn.repaint();

						End = true;
					} else if (Code.equals("YOULSE")) {
						stats.setText("You Lose..");
						OutCome = "LOS";
						Turn.setIcon(null);
						Turn.repaint();
						ComptTurn.setIcon(null);
						ComptTurn.repaint();

						End = true;

					} else if (Code.equals("GAMTIE")) {
						stats.setText("Game Tie.");
						OutCome = "TIE";

						Turn.setIcon(null);
						Turn.repaint();
						ComptTurn.setIcon(null);
						ComptTurn.repaint();

						End = true;
					}
				}
				// call player thread to stop
				message.println("GMQUIT");
			} catch (IOException t) {
				System.err.println("Server stopped.");
				stats.setText("Server stopped.");
				ShowErrMessageDialog("Server stopped.", "Error Message");
				End = true;
			} finally {
				try {
					TTTSocket.close();
				} catch (IOException t) {
					System.out.println("Error on closing client socket.");
				}
			}
		}

		public String GetName() {
			return PName;
		}

		public void ShowStatDialog(String output) {
			JOptionPane.showMessageDialog(this, output, PName + "Detail", JOptionPane.INFORMATION_MESSAGE);
		}

		public void ShowErrMessageDialog(String output, String title) {
			JOptionPane.showMessageDialog(this, output, title, JOptionPane.ERROR_MESSAGE);
		}

		public String GetGameResult() {
			return OutCome;
		}

		public void CleanAndRepaint() {
			for (int i = 0; i < 9; i++) {
				blanks[i].setIcon(null);
				blanks[i].repaint();
			}

			for (int i = 0; i <= none - 1; i++) {
				if (Old[i].substring(0, 2).equals("MY"))
					blanks[Integer.parseInt(Old[i].substring(2).trim())].setIcon(Icon);
				else
					blanks[Integer.parseInt(Old[i].substring(2).trim())].setIcon(ComptIcon);

				blanks[Integer.parseInt(Old[i].substring(2).trim())].repaint();
			}
		}

		public void RefillAllBlank() {

			for (int i = 0; i <= none - 1; i++) {
				if (Old[i].substring(0, 2).equals("MY"))
					blanks[Integer.parseInt(Old[i].substring(2).trim())].setIcon(Icon);
				else
					blanks[Integer.parseInt(Old[i].substring(2).trim())].setIcon(ComptIcon);

				blanks[Integer.parseInt(Old[i].substring(2).trim())].repaint();
			}
		}

		public boolean Again() {

			String temp = null;
			//set the message for play the game again or not 
			if (stats.getText().equals("You Win") || stats.getText().equals("You Lose")
					|| stats.getText().equals("Game Tied"))
				temp = stats.getText();
			else
				temp = "";

			int Reply = JOptionPane.showConfirmDialog(this, temp + "Want an another game?", "Game Message",
					JOptionPane.YES_NO_OPTION);

			if (JOptionPane.YES_OPTION == Reply)
				return true;
			else {
				stats.setText("Disconnected from server");
				return false;
			}
		}

		public void DeleteLastMove(int spot) {

			int OldSpot;

			for (int i = 0; i < none; i++) {

				OldSpot = Integer.parseInt(Old[i].substring(2).trim());

				// delete this move
				if (OldSpot == spot) {
					
					if ((i == 0) || (i == none - 1))
						Old[i] = null;
					else {
						for (int j = i; j < none - 1; j++)
							Old[j] = Old[j + 1];
					}
				}
			}

			none--;
			View = none - 1;
		}

		public boolean Moved() {
			String OldPlayer = null;
			boolean Result = false;
			for (int i = 0; i < none; i++) {
				OldPlayer = Old[i].substring(0, 2).trim();
				if (OldPlayer.equals("MY")) {
					Result = true;
					break;
				}
			}
			return Result;
		}

		public boolean AcceptReq() {
			int Reply = JOptionPane.showConfirmDialog(this, "Back one step?",
					"Game Message ", JOptionPane.YES_NO_OPTION);

			if (JOptionPane.YES_OPTION == Reply)
				return true;
			else
				return false;
		}

		// set function for buttons
		private class DirectionListener implements ActionListener {

			public void actionPerformed(ActionEvent t) {
				
				if (t.getSource() == BBegin) {
					View = -1;
					for (int i = 0; i < 9; i++) {
						blanks[i].setIcon(null);
						blanks[i].repaint();
					}

					stats.setText("First move");

				} else if (t.getSource() == BPrev) {
					if (View >= 0) {

						int BlankPointer = Integer.parseInt(Old[View].substring(2).trim());

						blanks[BlankPointer].setIcon(null);
						blanks[BlankPointer].repaint();

						View--;
					}

					stats.setText("Reviewing previous move");
				}

				else if (t.getSource() == BNext) {
					if (!(View == (none - 1))) {
						View++;
						int BlankPointer = Integer.parseInt(Old[View].substring(2).trim());

						if (Old[View].substring(0, 2).equals("MY"))
							blanks[BlankPointer].setIcon(Icon);
						else
							blanks[BlankPointer].setIcon(ComptIcon);

						blanks[BlankPointer].repaint();

						stats.setText("Reviewing previous move");
					} else
					// last move
					{
						stats.setText("Last move");
					}

				} else if (t.getSource() == BEnd) {
					View = none - 1;
					for (int i = 0; i <= none - 1; i++) {
						if (Old[i].substring(0, 2).equals("MY"))
							blanks[Integer.parseInt(Old[i].substring(2).trim())].setIcon(Icon);
						else
							blanks[Integer.parseInt(Old[i].substring(2).trim())].setIcon(ComptIcon);

						blanks[Integer.parseInt(Old[i].substring(2).trim())].repaint();
					}

					stats.setText("Last move");
				}
			}
		}
	}

	public static void main(String args[]) throws Exception {
		if ((args.length == 1) && (args[0].toUpperCase().equals("SERVER"))) {
			TicTT ServerHandlerThread = new TicTT("ServerThread");
			TicTT ClientHandlerThread = new TicTT("ClientThread");

			ServerHandlerThread.start();
			ClientHandlerThread.start();
		} else if (args.length == 2) {
			TicTT app = new TicTT(args[0], args[1]);
		} else {
			System.out.println("java TicTacToe client|server [ipaddress].");
		}
	}
}
