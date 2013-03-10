package edu.washington.cs.gscript.helpers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.xml.parsers.SAXParserFactory;

import edu.washington.cs.gscript.models.Category;
import edu.washington.cs.gscript.models.Gesture;
import edu.washington.cs.gscript.models.Project;
import edu.washington.cs.gscript.models.XYT;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class OneDollarDataImporter {

    private static class OneDollarXMLHandler extends DefaultHandler {

        private ArrayList<XYT> points = new ArrayList<XYT>();

        @Override
        public void startElement(String uri, String lName, String qName, Attributes attributes)
                throws SAXException {

            if (qName.equalsIgnoreCase("Point")) {

                double x = Double.parseDouble(attributes.getValue("X"));
                double y = Double.parseDouble(attributes.getValue("Y"));
                long t = Long.parseLong(attributes.getValue("T"));

                points.add(XYT.xyt(x, y, t));
            }
        }
    }

    public static ArrayList<Category> importDiretory(String dirName) {
        Project project = new Project();

        try {
            File dir = new File(dirName);
            for (String fileName : dir.list()) {

                if (!fileName.endsWith(".xml")) {
                    continue;
                }

                String name = fileName.substring(0, fileName.length() - 6);

                OneDollarXMLHandler handler = new OneDollarXMLHandler();
                SAXParserFactory.newInstance().newSAXParser().parse(
                        new File(dir.getPath() + File.separator + fileName), handler);

                if (!handler.points.isEmpty()) {
                    int index = project.findCategoryIndexByName(name);

                    if (index < 0) {
                        Category category = new Category(name);
                        project.importCategories(Arrays.asList(category));
                        index = project.findCategoryIndexByName(name);
                    }

                    project.addSample(
                            project.getCategories().get(index),
                            new Gesture(handler.points.toArray(new XYT[handler.points.size()])));
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }

        return project.getCategories();
    }

    public static ArrayList<Category> importTemplate(String fileName) {
        Project project = new Project();

        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));

            while (true) {
                String line = in.readLine();

                if (line == null || line.trim().isEmpty()) {
                    break;
                }

                String name = line.trim();

                ArrayList<XYT> points = new ArrayList<XYT>();

                String[] values = in.readLine().split(",");
                for (int i = 0; i < values.length; i += 2) {
                    points.add(XYT.xyt(Double.parseDouble(values[i]), Double.parseDouble(values[i+1]), -1));
                }

                int index = project.findCategoryIndexByName(name);
                if (index < 0) {
                    Category category = new Category(name);
                    project.importCategories(Arrays.asList(category));
                    index = project.findCategoryIndexByName(name);
                }

                project.addSample(
                        project.getCategories().get(index),
                        new Gesture(points.toArray(new XYT[points.size()])));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return project.getCategories();
    }
}
