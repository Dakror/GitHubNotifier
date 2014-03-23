package de.dakror.ghnotifier;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.border.AbstractBorder;

public class RoundedBorder extends AbstractBorder
{
	private static final long serialVersionUID = 1L;
	Color color;
	
	public RoundedBorder(Color c)
	{
		color = c;
	}
	
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
	{
		Graphics2D g2 = (Graphics2D) g;
		g2.setColor(color);
		int arc = 10;
		g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
	}
}
