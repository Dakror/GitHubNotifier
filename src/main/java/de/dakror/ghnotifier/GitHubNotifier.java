/*******************************************************************************
 * Copyright 2015 Maximilian Stark | Dakror <mail@dakror.de>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/


package de.dakror.ghnotifier;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;

import org.eclipse.egit.github.core.Authorization;
import org.eclipse.egit.github.core.Commit;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.PageIterator;
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.event.CommitCommentPayload;
import org.eclipse.egit.github.core.event.Event;
import org.eclipse.egit.github.core.event.IssueCommentPayload;
import org.eclipse.egit.github.core.event.IssuesPayload;
import org.eclipse.egit.github.core.event.PushPayload;
import org.eclipse.egit.github.core.service.CommitService;
import org.eclipse.egit.github.core.service.EventService;
import org.eclipse.egit.github.core.service.MarkdownService;
import org.eclipse.egit.github.core.service.OAuthService;

import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.codecs.CodecWav;
import paulscode.sound.libraries.LibraryJavaSound;

public class GitHubNotifier extends JFrame {
	class UpdateThread extends Thread {
		long newest = 0;
		
		public UpdateThread() {
			newest = System.currentTimeMillis();
		}
		
		@Override
		public void run() {
			try {
				while (true) {
					ArrayList<Event> arr = getEvents();
					
					for (int i = 0; i < arr.size(); i++) {
						if (arr.get(i).getCreatedAt().getTime() > newest) {
							ss.setMasterVolume(1.0f);
							ss.quickPlay(true, getClass().getResource("/Notification.wav"), "Notification.wav", false, 0, 0, 0, SoundSystemConfig.ATTENUATION_ROLLOFF, SoundSystemConfig.getDefaultRolloff());
							addEvent(arr.get(i), false);
						} else break;
					}
					
					newest = System.currentTimeMillis();
					
					Thread.sleep(7500);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	
	private static final long serialVersionUID = 1L;
	
	public static final File DIR = new File(System.getProperty("user.home") + "/.dakror/GitHub Notifier/");
	
	public static final String DIFF_ADDED = "<span style='font-family:octicons;color:#6cc644'>\uf06b</span>";
	public static final String DIFF_MODIFIED = "<span style='font-family:octicons;color:#d0b44c'>\uf06d</span>";
	public static final String DIFF_REMOVED = "<span style='font-family:octicons;color:#bd2c00'>\uf06c</span>";
	public static final String DIFF_RENAMED = "<span style='font-family:octicons;color:#677a85'>\uf06e</span>";
	
	public static final int w = 580;
	public static final int h = 140;
	
	String token;
	SoundSystem ss;
	GitHubClient client;
	
	public GitHubNotifier(final SoundSystem ss) {
		super("GitHubNotifier");
		
		client = new GitHubClient();
		
		this.ss = ss;
		
		try {
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(Font.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/octicons.ttf")).deriveFont(20f));
			loadRemindMe();
			DIR.mkdirs();
			
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					ss.cleanup();
				}
			});
			setDefaultCloseOperation(EXIT_ON_CLOSE);
			setResizable(false);
			init();
			setLocationRelativeTo(null);
			setVisible(true);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
	
	public void init() {
		JMenuBar menu = new JMenuBar();
		JMenu f = new JMenu("Help");
		f.add(new JMenuItem(new AbstractAction("Change User") {
			private static final long serialVersionUID = 1L;
			
			@Override
			public void actionPerformed(ActionEvent e) {
				token = null;
				saveRemindMe(false);
				init();
				
			}
		}));
		menu.add(f);
		setJMenuBar(menu);
		
		getContentPane().removeAll();
		repaint();
		
		if (token == null) {
			JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
			panel.setPreferredSize(new Dimension(250, 170));
			JLabel l = new JLabel("Login", JLabel.CENTER);
			l.setFont(l.getFont().deriveFont(Font.BOLD, 35f));
			l.setPreferredSize(new Dimension(230, 50));
			panel.add(l);
			
			l = new JLabel("Username:");
			l.setPreferredSize(new Dimension(110, 20));
			panel.add(l);
			
			l = new JLabel("Password:");
			l.setPreferredSize(new Dimension(110, 20));
			panel.add(l);
			
			final JButton go = new JButton("Login");
			
			final JTextField usr = new JTextField();
			usr.setPreferredSize(new Dimension(110, 20));
			panel.add(usr);
			
			final JPasswordField pwd = new JPasswordField();
			pwd.setPreferredSize(new Dimension(110, 20));
			panel.add(pwd);
			
			DocumentListener dl = new DocumentListener() {
				@Override
				public void removeUpdate(DocumentEvent e) {
					go.setEnabled(usr.getText().length() > 0 && pwd.getPassword().length > 0);
				}
				
				@Override
				public void insertUpdate(DocumentEvent e) {
					go.setEnabled(usr.getText().length() > 0 && pwd.getPassword().length > 0);
				}
				
				@Override
				public void changedUpdate(DocumentEvent e) {
					go.setEnabled(usr.getText().length() > 0 && pwd.getPassword().length > 0);
				}
			};
			usr.getDocument().addDocumentListener(dl);
			pwd.getDocument().addDocumentListener(dl);
			
			l = new JLabel("Remember me:");
			l.setPreferredSize(new Dimension(105, 20));
			panel.add(l);
			
			final JCheckBox rmd = new JCheckBox();
			panel.add(rmd);
			
			go.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						client.setCredentials(usr.getText(), new String(pwd.getPassword()));
						
						Authorization auth = new Authorization();
						auth.setNote("GitHub Notifier");
						ArrayList<String> scopes = new ArrayList<>();
						scopes.add("repo:status");
						auth.setScopes(scopes);
						
						
						OAuthService oauth = new OAuthService(client);
						boolean created = false;
						for (Authorization a : oauth.getAuthorizations()) {
							if (a.getNote() != null && a.getNote().equals(auth.getNote())) {
								if (a.getToken().trim().length() > 0) {
									auth = a;
									created = true;
								} else {
									oauth.deleteAuthorization(a.getId());
								}
								break;
							}
						}
						
						if (!created) {
							auth = oauth.createAuthorization(auth);
						}
						
						token = auth.getToken();
						saveRemindMe(rmd.isSelected());
						
						init();
					} catch (RequestException e1) {
						if (e1.getStatus() == 401) {
							JOptionPane.showMessageDialog(GitHubNotifier.this, "Bad login!", "Error!", JOptionPane.ERROR_MESSAGE);
							pwd.setText("");
						}
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					
				}
			});
			go.setEnabled(false);
			go.setPreferredSize(new Dimension(230, 20));
			panel.add(go);
			
			setContentPane(panel);
		} else {
			Box list = Box.createVerticalBox();
			JScrollPane listWrap = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			listWrap.setPreferredSize(new Dimension(600, 350));
			listWrap.getVerticalScrollBar().setUnitIncrement(20);
			listWrap.getViewport().setBackground(Color.decode("#1a1a1a"));
			setContentPane(listWrap);
			
			loadEvents();
		}
		revalidate();
		pack();
		setLocationRelativeTo(null);
	}
	
	public void loadRemindMe() throws Exception {
		File file = new File(DIR, "login.txt");
		if (file.exists()) {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String[] parts = br.readLine().trim().split(":");
			br.close();
			if (parts.length < 2) return;
			
			client.setCredentials(parts[0], null);
			client.setOAuth2Token(parts[1]);
			token = parts[1];
		}
	}
	
	public void saveRemindMe(boolean remind) {
		File file = new File(DIR, "login.txt");
		if (token == null || !remind) {
			file.delete();
			return;
		}
		
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			bw.write(client.getUser() + ":" + token);
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void addEvent(final Event e, boolean append) throws Exception {
		if (token == null) return;
		
		final JScrollPane p = (JScrollPane) getContentPane();
		Box box = (Box) p.getViewport().getView();
		JPanel panel = null;
		switch (e.getType()) {
			case "PushEvent": {
				panel = getUIForPushEvent(p, e);
				break;
			}
			case "IssueCommentEvent": {
				panel = getUIForIssueCommentEvent(p, e);
				break;
			}
			case "IssuesEvent": {
				panel = getUIForIssuesEvent(p, e);
				break;
			}
			case "CommitCommentEvent": {
				panel = getUIForCommitCommentEvent(p, e);
				break;
			}
		}
		
		if (panel != null) {
			final JPanel p1 = panel;
			for (Component c1 : panel.getComponents()) {
				c1.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent ev) {
						p1.setOpaque(true);
						p1.setBackground(Color.decode("#292929"));
						p1.setBorder(new RoundedBorder(Color.decode("#096ac8")));
					}
					
					@Override
					public void mouseExited(MouseEvent ev) {
						p1.setOpaque(false);
						p1.setBorder(new RoundedBorder(Color.black));
					}
				});
			}
			
			if (append) box.add(panel);
			else box.add(panel, 0);
			
			JPanel glue = new JPanel();
			glue.setOpaque(false);
			glue.setPreferredSize(new Dimension(w, 20));
			if (append) box.add(glue);
			else box.add(glue, 1);
		}
		
		box.revalidate();
		p.setViewportView(box);
		p.getViewport().setBackground(Color.decode("#1a1a1a"));
		revalidate();
		repaint();
	}
	
	// -- UI functions -- //
	
	public JPanel getUIForPushEvent(final JScrollPane p, final Event e) throws Exception {
		final JPanel panel = new JPanel(null);
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent ev) {
				panel.setOpaque(true);
				panel.setBackground(Color.decode("#292929"));
				panel.setBorder(new RoundedBorder(Color.decode("#096ac8")));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				panel.setOpaque(false);
				panel.setBorder(new RoundedBorder(Color.black));
			}
		});
		panel.setOpaque(false);
		panel.setBorder(new RoundedBorder(Color.black));
		
		panel.setPreferredSize(new Dimension(w, h));
		JLabel title = new JLabel("<html><body style='font-family:Arial;font-weight:bold;color:#f6f6f6;padding-left:6px;font-size:13px;'>" + e.getActor().getLogin() + " pushed to " + ((PushPayload) e.getPayload()).getRef().substring(((PushPayload) e.getPayload()).getRef().lastIndexOf("/") + 1) + " at " + e.getRepo().getName() + "</body></html>");
		title.setBounds(0, 0, w - 10, 40);
		
		TexturedPanel wrap = new TexturedPanel(getImage("ui-bg_diagonals-thick_15_0b3e6f_40x40.png"));
		wrap.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent ev) {
				try {
					Desktop.getDesktop().browse(new URL(((PushPayload) e.getPayload()).getCommits().get(0).getUrl().replace("api.", "").replace("/repos", "").replace("/commits", "/commit")).toURI());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public void mouseEntered(MouseEvent ev) {
				GitHubNotifier.this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				GitHubNotifier.this.setCursor(Cursor.getDefaultCursor());
			}
		});
		wrap.setLayout(null);
		wrap.add(title);
		wrap.setBounds(5, 5, w - 10, 40);
		panel.add(wrap);
		final JLabel desc = new JLabel("<html><body style=\"color: #d9d9d9;font-size:12px\">" + new SimpleDateFormat("dd.MM.yyyy, HH:mm").format(e.getCreatedAt()) + ": " + ((PushPayload) e.getPayload()).getCommits().get(0).getMessage() + "</body></html>");
		desc.setVerticalAlignment(JLabel.TOP);
		desc.setBounds(15, 50, w - 30, 100000);
		final JScrollPane descScroll = new JScrollPane(desc, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		descScroll.addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (descScroll.getViewport().getViewSize().height <= descScroll.getViewport().getVisibleRect().height) {
					p.dispatchEvent(e);
				}
			}
		});
		descScroll.getVerticalScrollBar().setUnitIncrement(2);
		descScroll.setBounds(15, 50, w - 20, h - 70);
		descScroll.setOpaque(false);
		descScroll.setBorder(BorderFactory.createEmptyBorder());
		descScroll.getViewport().setOpaque(false);
		descScroll.getViewport().setLayout(null);
		panel.add(descScroll);
		
		final JSeparator sp = new JSeparator(JSeparator.VERTICAL);
		sp.setBounds(w / 2 - 1, 48, 2, 90);
		sp.setVisible(false);
		panel.add(sp);
		
		JPanel list = new JPanel();
		list.setBounds(0, 0, w / 2 - 20, 10000);
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		
		Commit commit0 = ((PushPayload) e.getPayload()).getCommits().get(0);
		RepositoryId repid = RepositoryId.createFromUrl(e.getRepo().getUrl().replace("/repos", ""));
		CommitService cs = new CommitService(client);
		RepositoryCommit rc = cs.getCommit(repid, commit0.getSha());
		for (int i = 0; i < rc.getFiles().size(); i++) {
			JLabel label = new JLabel("<html><body style='font-size:12px;'>" + getClass().getField("DIFF_" + rc.getFiles().get(i).getStatus().toUpperCase()).get(null) + "<span style='font-family:Courier;color:#4183c4;'>&nbsp;" + rc.getFiles().get(i).getFilename().substring(rc.getFiles().get(i).getFilename().lastIndexOf("/") + 1) + "</span></body></html>");
			list.add(label);
		}
		final JScrollPane jsp = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		jsp.setBounds(w / 2 + 5, 50, w / 2 - 10, h - 50);
		jsp.setOpaque(false);
		jsp.addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (jsp.getViewport().getViewSize().height <= jsp.getViewport().getVisibleRect().height) {
					p.dispatchEvent(e);
				}
			}
		});
		jsp.getVerticalScrollBar().setUnitIncrement(5);
		jsp.getViewport().setOpaque(false);
		jsp.setBorder(BorderFactory.createEmptyBorder());
		jsp.setVisible(false);
		panel.add(jsp);
		
		final JToggleButton toggle = new JToggleButton("Show Diff", false);
		toggle.setBounds(15, h - 20, 80, 20);
		toggle.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if (toggle.isSelected()) {
					toggle.setText("Hide Diff");
					descScroll.setBounds(15, 50, w / 2 - 20, h - 70);
					descScroll.getViewport().getView().setBounds(0, 0, w / 2 - 30, 100000);
				} else {
					toggle.setText("Show Diff");
					descScroll.setBounds(15, 50, w - 20, h - 70);
					descScroll.getViewport().getView().setBounds(0, 0, w - 30, 100000);
				}
				jsp.setVisible(toggle.isSelected());
				sp.setVisible(toggle.isSelected());
			}
		});
		panel.add(toggle);
		
		return panel;
	}
	
	public JPanel getUIForIssueCommentEvent(final JScrollPane p, final Event e) throws Exception {
		final JPanel panel = new JPanel(null);
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent ev) {
				panel.setOpaque(true);
				panel.setBackground(Color.decode("#292929"));
				panel.setBorder(new RoundedBorder(Color.decode("#096ac8")));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				panel.setOpaque(false);
				panel.setBorder(new RoundedBorder(Color.black));
			}
		});
		panel.setOpaque(false);
		panel.setBorder(new RoundedBorder(Color.black));
		
		panel.setPreferredSize(new Dimension(w, h));
		JLabel title = new JLabel("<html><body style='font-family:Arial;font-weight:bold;color:#f6f6f6;padding-left:6px;font-size:13px;'>" + e.getActor().getLogin() + " commented on issue " + e.getRepo().getName() + "#" + ((IssueCommentPayload) e.getPayload()).getIssue().getNumber() + "</body></html>");
		title.setBounds(0, 0, w - 10, 40);
		
		TexturedPanel wrap = new TexturedPanel(getImage("ui-bg_diagonals-thick_15_0b3e6f_40x40.png"));
		wrap.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent ev) {
				try {
					Desktop.getDesktop().browse(new URL("https://github.com/" + e.getRepo().getName() + "/issues/" + ((IssueCommentPayload) e.getPayload()).getIssue().getNumber() + "#issuecomment-" + ((IssueCommentPayload) e.getPayload()).getComment().getId()).toURI());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public void mouseEntered(MouseEvent ev) {
				GitHubNotifier.this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				GitHubNotifier.this.setCursor(Cursor.getDefaultCursor());
			}
		});
		wrap.setLayout(null);
		wrap.add(title);
		wrap.setBounds(5, 5, w - 10, 40);
		panel.add(wrap);
		final JEditorPane desc = new JEditorPane();
		desc.setEditorKit(JEditorPane.createEditorKitForContentType("text/html"));
		desc.setText("<html><body style=\"color: #d9d9d9;font-size:12px;font-family:Arial;\">" + new SimpleDateFormat("dd.MM.yyyy, HH:mm").format(e.getCreatedAt()) + ": " + new MarkdownService(client).getHtml(((IssueCommentPayload) e.getPayload()).getComment().getBody(), MarkdownService.MODE_GFM).substring(3) + "</body></html>");
		((HTMLDocument) desc.getDocument()).getStyleSheet().addRule("a {color:#9999ff;}");
		desc.setEditable(false);
		desc.setOpaque(false);
		desc.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent ev) {
				panel.setOpaque(true);
				panel.setBackground(Color.decode("#292929"));
				panel.setBorder(new RoundedBorder(Color.decode("#096ac8")));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				panel.setOpaque(false);
				panel.setBorder(new RoundedBorder(Color.black));
			}
		});
		desc.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent e) {
				try {
					if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) Desktop.getDesktop().browse(e.getURL().toURI());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		
		desc.setBounds(15, 50, w - 30, 100000);
		final JScrollPane descScroll = new JScrollPane(desc, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		descScroll.addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (descScroll.getViewport().getViewSize().height <= descScroll.getViewport().getVisibleRect().height) {
					p.dispatchEvent(e);
				}
			}
		});
		descScroll.getVerticalScrollBar().setUnitIncrement(2);
		descScroll.setBounds(15, 50, w - 20, h - 60);
		descScroll.setOpaque(false);
		descScroll.setBorder(BorderFactory.createEmptyBorder());
		descScroll.getViewport().setOpaque(false);
		descScroll.getViewport().setLayout(null);
		panel.add(descScroll);
		
		return panel;
	}
	
	public JPanel getUIForIssuesEvent(final JScrollPane p, final Event e) throws Exception {
		final JPanel panel = new JPanel(null);
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent ev) {
				panel.setOpaque(true);
				panel.setBackground(Color.decode("#292929"));
				panel.setBorder(new RoundedBorder(Color.decode("#096ac8")));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				panel.setOpaque(false);
				panel.setBorder(new RoundedBorder(Color.black));
			}
		});
		panel.setOpaque(false);
		panel.setBorder(new RoundedBorder(Color.black));
		
		panel.setPreferredSize(new Dimension(w, h));
		JLabel title = new JLabel("<html><body style='font-family:Arial;font-weight:bold;color:#f6f6f6;padding-left:6px;font-size:13px;'>" + e.getActor().getLogin() + " openend issue " + e.getRepo().getName() + "#" + ((IssuesPayload) e.getPayload()).getIssue().getNumber() + "</body></html>");
		title.setBounds(0, 0, w - 10, 40);
		
		TexturedPanel wrap = new TexturedPanel(getImage("ui-bg_diagonals-thick_15_0b3e6f_40x40.png"));
		wrap.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent ev) {
				try {
					Desktop.getDesktop().browse(new URL("https://github.com/" + e.getRepo().getName() + "/issues/" + ((IssuesPayload) e.getPayload()).getIssue().getNumber()).toURI());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public void mouseEntered(MouseEvent ev) {
				GitHubNotifier.this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				GitHubNotifier.this.setCursor(Cursor.getDefaultCursor());
			}
		});
		wrap.setLayout(null);
		wrap.add(title);
		wrap.setBounds(5, 5, w - 10, 40);
		panel.add(wrap);
		final JLabel desc = new JLabel("<html><body style=\"color: #d9d9d9;font-size:12px\">" + new SimpleDateFormat("dd.MM.yyyy, HH:mm").format(e.getCreatedAt()) + ": " + ((IssuesPayload) e.getPayload()).getIssue().getTitle() + "</body></html>");
		desc.setVerticalAlignment(JLabel.TOP);
		desc.setBounds(15, 50, w - 30, 100000);
		final JScrollPane descScroll = new JScrollPane(desc, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		descScroll.addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (descScroll.getViewport().getViewSize().height <= descScroll.getViewport().getVisibleRect().height) {
					p.dispatchEvent(e);
				}
			}
		});
		descScroll.getVerticalScrollBar().setUnitIncrement(2);
		descScroll.setBounds(15, 50, w - 20, h - 60);
		descScroll.setOpaque(false);
		descScroll.setBorder(BorderFactory.createEmptyBorder());
		descScroll.getViewport().setOpaque(false);
		descScroll.getViewport().setLayout(null);
		panel.add(descScroll);
		
		return panel;
	}
	
	public JPanel getUIForCommitCommentEvent(final JScrollPane p, final Event e) throws Exception {
		final JPanel panel = new JPanel(null);
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent ev) {
				panel.setOpaque(true);
				panel.setBackground(Color.decode("#292929"));
				panel.setBorder(new RoundedBorder(Color.decode("#096ac8")));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				panel.setOpaque(false);
				panel.setBorder(new RoundedBorder(Color.black));
			}
		});
		panel.setOpaque(false);
		panel.setBorder(new RoundedBorder(Color.black));
		
		panel.setPreferredSize(new Dimension(w, h));
		JLabel title = new JLabel("<html><body style='font-family:Arial;font-weight:bold;color:#f6f6f6;padding-left:6px;font-size:13px;'>" + e.getActor().getLogin() + " commented on commit " + e.getRepo().getName() + "@" + ((CommitCommentPayload) e.getPayload()).getComment().getCommitId().substring(0, 10) + "</body></html>");
		title.setBounds(0, 0, w - 10, 40);
		
		TexturedPanel wrap = new TexturedPanel(getImage("ui-bg_diagonals-thick_15_0b3e6f_40x40.png"));
		wrap.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent ev) {
				try {
					Desktop.getDesktop().browse(new URL(((CommitCommentPayload) e.getPayload()).getComment().getUrl()).toURI());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public void mouseEntered(MouseEvent ev) {
				GitHubNotifier.this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				GitHubNotifier.this.setCursor(Cursor.getDefaultCursor());
			}
		});
		wrap.setLayout(null);
		wrap.add(title);
		wrap.setBounds(5, 5, w - 10, 40);
		panel.add(wrap);
		final JEditorPane desc = new JEditorPane();
		desc.setEditorKit(JEditorPane.createEditorKitForContentType("text/html"));
		desc.setText("<html><body style=\"color: #d9d9d9;font-size:12px;font-family:Arial;\">" + new SimpleDateFormat("dd.MM.yyyy, HH:mm").format(e.getCreatedAt()) + ": " + new MarkdownService(client).getHtml(((CommitCommentPayload) e.getPayload()).getComment().getBody(), MarkdownService.MODE_GFM).substring(3) + "</body></html>");
		((HTMLDocument) desc.getDocument()).getStyleSheet().addRule("a {color:#9999ff;}");
		desc.setEditable(false);
		desc.setOpaque(false);
		desc.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent ev) {
				panel.setOpaque(true);
				panel.setBackground(Color.decode("#292929"));
				panel.setBorder(new RoundedBorder(Color.decode("#096ac8")));
			}
			
			@Override
			public void mouseExited(MouseEvent ev) {
				panel.setOpaque(false);
				panel.setBorder(new RoundedBorder(Color.black));
			}
		});
		desc.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent e) {
				try {
					if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) Desktop.getDesktop().browse(e.getURL().toURI());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		desc.setBounds(15, 50, w - 30, 100000);
		final JScrollPane descScroll = new JScrollPane(desc, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		descScroll.addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (descScroll.getViewport().getViewSize().height <= descScroll.getViewport().getVisibleRect().height) {
					p.dispatchEvent(e);
				}
			}
		});
		descScroll.getVerticalScrollBar().setUnitIncrement(2);
		descScroll.setBounds(15, 50, w - 20, h - 60);
		descScroll.setOpaque(false);
		descScroll.setBorder(BorderFactory.createEmptyBorder());
		descScroll.getViewport().setOpaque(false);
		descScroll.getViewport().setLayout(null);
		panel.add(descScroll);
		
		return panel;
	}
	
	// -- //
	
	public Image getImage(String s) {
		try {
			return ImageIO.read(getClass().getResource("/img/" + s));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	// -- GitHub functions -- //
	public ArrayList<Event> getEvents() {
		EventService es = new EventService(client);
		PageIterator<Event> pi = es.pageUserReceivedEvents(client.getUser(), true, 30);
		return new ArrayList<>(pi.next());
	}
	
	public void loadEvents() {
		new Thread() {
			@Override
			public void run() {
				try {
					ArrayList<Event> page = getEvents();
					
					for (int i = 0; i < page.size(); i++)
						addEvent(page.get(i), true);
					
					new UpdateThread().start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
	}
	
	// -- static functions -- //
	
	public static String getURLContent(InputStream is) {
		String res = "", line = "";
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null)
				res += line + "\r\n";
			br.close();
		} catch (IOException e) {
			return null;
		}
		return res;
	}
	
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			SoundSystemConfig.addLibrary(LibraryJavaSound.class);
			SoundSystemConfig.setCodec("wav", CodecWav.class);
			SoundSystem ss = new SoundSystem(LibraryJavaSound.class);
			new GitHubNotifier(ss);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
