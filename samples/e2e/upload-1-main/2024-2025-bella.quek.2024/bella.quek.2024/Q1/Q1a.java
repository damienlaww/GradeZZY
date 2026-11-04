/*
 * Name: Bella Quek
 * Email ID: bella.quek.2024@smu.edu.sg
 */

import java.util.*;

public class Q1a {

    /*
     * Write the method getIsogramWords
     */
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> collected2 = new ArrayList<>();
        for (String cur2 : inputs) {
            String letters = "";
            boolean unique = true;
            for (int k = 0; k < cur2.length(); k++) {
                String c = cur2.substring(k, k + 1).toUpperCase();
                if (letters.contains(c)) {
                    unique = false;
                    break;
                }
                letters += c;
            }
            if (unique) {
                collected2.add(cur2);
            }
        }
        return collected2;
    }


    // Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            ArrayList<String> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add("cat");
            inputs.add("dog");
            inputs.add("tiger");

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
            String expected = "[cat, dog, tiger]";
            ArrayList<String> actual = getIsogramWords(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected.equals(actual.toString())) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<String> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add("alan");
            inputs.add("ben");
            inputs.add("cindy");
            inputs.add("danny");
            inputs.add("evan");
            
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
            String expected = "[ben, cindy, evan]";
            ArrayList<String> actual = getIsogramWords(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected.equals(actual.toString())) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<String> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add("Alan");
            inputs.add("Ben");
            inputs.add("Cindy");
            inputs.add("Danny");
            inputs.add("Evelyne");
            
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
            String expected = "[Ben, Cindy]";
            ArrayList<String> actual = getIsogramWords(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected.equals(actual.toString())) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
    }
}