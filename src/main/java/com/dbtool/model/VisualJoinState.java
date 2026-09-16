package com.dbtool.model;

import java.util.ArrayList;
import java.util.List;

public class VisualJoinState {
    public String baseTable;
    public boolean distinct;
    public List<String> selectedColumns = new ArrayList<>();
    public List<JoinState> joins = new ArrayList<>();
    public List<WhereState> wheres = new ArrayList<>();
    public List<OrderByState> orderBys = new ArrayList<>();
    public String limit;
    public String offset;

    public static class JoinState {
        public String type;
        public String table;
        public String leftTable;
        public String leftCol;
        public String rightCol;
    }

    public static class WhereState {
        public String logic;
        public String table;
        public String column;
        public String operator;
        public String value;
    }

    public static class OrderByState {
        public String table;
        public String column;
        public String direction;
    }
}
