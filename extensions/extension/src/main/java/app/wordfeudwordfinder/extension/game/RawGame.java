package app.wordfeudwordfinder.extension.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RawGame {
    private final long id;
    private final int boardId;
    private final int rulesetId;
    private final int currentPlayer;
    private final int endGame;
    private final int localPosition;
    private final List<LogEvent> log;

    public RawGame(long id, int boardId, int rulesetId, int currentPlayer, int endGame,
                   int localPosition, List<LogEvent> log) {
        this.id = id;
        this.boardId = boardId;
        this.rulesetId = rulesetId;
        this.currentPlayer = currentPlayer;
        this.endGame = endGame;
        this.localPosition = localPosition;
        this.log = Collections.unmodifiableList(new ArrayList<>(log));
    }

    public long getId() { return id; }
    public int getBoardId() { return boardId; }
    public int getRulesetId() { return rulesetId; }
    public int getCurrentPlayer() { return currentPlayer; }
    public int getEndGame() { return endGame; }
    public int getLocalPosition() { return localPosition; }
    public List<LogEvent> getLog() { return log; }
}
