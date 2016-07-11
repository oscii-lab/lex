package org.oscii.corpus;

import java.util.ArrayList;

/**
 * A compact posting list.
 */
public class PostingList extends ArrayList<Long> {
  // TODO enforce that it stays EMPTY
  public static PostingList EMPTY = new PostingList(0);

  public PostingList() {
    this(1);
  }

  public PostingList(int k) {
    super(k);
  }

  public void addPosting(int line, int position) {
    add(newPosting(line, position));
  }

  public int line(int index) {
    return lineOfPosting(get(index));
  }

  public int position(int index) {
    return positionOfPosting(get(index));
  }

  public static int lineOfPosting(long posting) {
    return (int) (posting >> 32);
  }

  public static int positionOfPosting(long posting) {
    return (int) posting;
  }

  public static long newPosting(int line, int position) {
    return (long) line << 32 | position & 0xFFFFFFFFL;
  }
}
