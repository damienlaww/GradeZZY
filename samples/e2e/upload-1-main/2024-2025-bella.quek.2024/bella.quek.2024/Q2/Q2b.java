/*
 * Name: Bella Quek
 * Email ID: bella.quek.2024@smu.edu.sg
 */

import java.util.*;
import java.io.*;

public class Q2b {

     /*
     * Write the method getTopStudent
     */
    public static String getTopStudent(String filename, String courseName) {
        String bestIdx2 = "";
        double highest = -1.0;
        boolean found = false;
        Scanner sc22;
        try {
            sc22 = new Scanner(new File(filename));
        } catch (FileNotFoundException ex) {
            throw new DataException();
        }
        while (sc22.hasNextLine()) {
            String l2 = sc22.nextLine();
            int comma = l2.indexOf(',');
            String studentName = l2.substring(0, comma);
            String[] records = l2.substring(comma + 1).split("-");
            for (String rec : records) {
                int hash = rec.indexOf('#');
                if (rec.substring(0, hash).equals(courseName)) {
                    double gpa = Double.parseDouble(rec.substring(hash + 1));
                    if (!found || gpa > highest) {
                        highest = gpa;
                        bestIdx2 = studentName;
                        found = true;
                    }
                }
            }
        }
        sc22.close();
        if (!found) {
            throw new DataException();
        }
        return bestIdx2 + "-" + highest;
    }

    //Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS101");
            String expected = "John LEE-4.0";
            String actual = getTopStudent("students.txt", "IS101");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS102");
            String expected = "Mary CHAN-3.5";
            String actual = getTopStudent("students.txt", "IS102");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            } System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS103");
            String expected = "CHAN Wei Jun-4.0";
            String actual = getTopStudent("students.txt", "IS103");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS104");
            try{
                String actual = getTopStudent("students.txt", "IS104");
                System.out.println("Failed -> Expecting a Data Exception");
            } catch (DataException ex){
                System.out.println("Passed");
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "nosuchfile.txt", "IS101");
            try{
                String actual = getTopStudent("nosuchfile.txt", "IS101");
                System.out.println("Failed -> Expecting a Data Exception");
            } catch (DataException ex){
                System.out.println("Passed");
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
        }
    } 
}