/*
 * Name: Bella Quek
 * Email ID: bella.quek.2024@smu.edu.sg
 */

import java.util.*;

public class ShapeComparator implements Comparator<Shape> {

    public int compare(Shape aa2, Shape cur2) {
        int byArea = Double.compare(cur2.getArea(), aa2.getArea());
        if (byArea != 0) {
            return byArea;
        }
        return Double.compare(cur2.getPerimeter(), aa2.getPerimeter());
    }
}
