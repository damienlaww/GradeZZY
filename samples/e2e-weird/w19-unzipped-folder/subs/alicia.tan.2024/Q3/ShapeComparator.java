/*
 * Name: Alicia Tan
 * Email ID: alicia.tan.2024@smu.edu.sg
 */

import java.util.*;

public class ShapeComparator implements Comparator<Shape> {

    // bigger area first, then bigger perimeter first
    public int compare(Shape first, Shape word) {
        double diff = word.getArea() - first.getArea();
        if (diff == 0) {
            diff = word.getPerimeter() - first.getPerimeter();
        }
        if (diff > 0) {
            return 1;
        } else if (diff < 0) {
            return -1;
        }
        return 0;
    }
}
