package com.example.hiring_data_app;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {
    Triple<List<Integer>, List<Integer>, List<String>> result;
    RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button fetch_data = findViewById(R.id.fetch_data);
        ImageButton gotop = findViewById(R.id.gotop);
        fetch_data.setText("Fetch Data");

        fetch_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fetch_data.setText("Refreshing...");

                // Calling the function to perform API call
                result = fetchData();

                // Initializes the List view by groups
                initializeList();

                fetch_data.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        fetch_data.setText("Fetch Data");
                    }
                }, 1000);
            }
        });

        gotop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recyclerView.smoothScrollToPosition(0);
            }
        });
    }

    // Miscellaneous function to pass three lists across the functions in App
    public class Triple<X, Y, Z> {
        public final X first;
        public final Y second;
        public final Z third;

        public Triple(X first, Y second, Z third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }

    // Initializing the list for display
    private void initializeList() {
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (result != null) {
            // Filter out items with blank or null names
            List<Triple<Integer, Integer, String>> filteredData = new ArrayList<>();
            for (int i = 0; i < result.third.size(); i++) {
                if (result.third.get(i) != null && !result.third.get(i).isEmpty()) {
                    filteredData.add(new Triple<>(result.first.get(i), result.second.get(i), result.third.get(i)));
                }
            }

            // Sort the filtered data by listId and name
            Collections.sort(filteredData, new Comparator<Triple<Integer, Integer, String>>() {
                @Override
                public int compare(Triple<Integer, Integer, String> item1, Triple<Integer, Integer, String> item2) {
                    if (item1.second.equals(item2.second)) {
                        return item1.third.compareTo(item2.third);
                    }
                    return item1.second.compareTo(item2.second);
                }
            });

            // Group the sorted data by listId
            Map<Integer, List<Triple<Integer, Integer, String>>> groupedData = new HashMap<>();
            int listId = 0;
            for (Triple<Integer, Integer, String> item : filteredData) {
                listId = item.second;
                if (!groupedData.containsKey(listId)) {
                    groupedData.put(listId, new ArrayList<>());
                }
                groupedData.get(listId).add(item);
            }

            List<Integer> listIds = new ArrayList<>(groupedData.keySet());

            GroupedAdapter adapter = new GroupedAdapter(this, listIds, groupedData);
            recyclerView.setAdapter(adapter);
        }
    }



    // The main API call function
    private Triple<List<Integer>, List<Integer>, List<String>> fetchData() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Callable<String> fetchTask = () -> {
            try {
                URL url = new URL("https://fetch-hiring.s3.amazonaws.com/hiring.json");
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = httpURLConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder resultBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    resultBuilder.append(line);
                }
                return resultBuilder.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        Future<String> futureResult = executor.submit(fetchTask);

        try {
            String result = futureResult.get();
            return extractIdsFromJsonResponse(result);
        } catch (Exception e) {
            e.printStackTrace();
        }

        executor.shutdown();
        return null;
    }

    // Parsing the JSON response and creating the custom array lists
    private Triple<List<Integer>, List<Integer>, List<String>> extractIdsFromJsonResponse(String jsonResponse) {
        List<Integer> ids = new ArrayList<>();
        List<Integer> listIds = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(jsonResponse);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                int id = jsonObject.getInt("id");
                int listId = jsonObject.getInt("listId");
                String name = jsonObject.getString("name");
                ids.add(id);
                listIds.add(listId);
                names.add(name);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new Triple<>(ids, listIds, names);
    }


    // Custom Adapter to bind the list based on the array list for individual fields
    public class GroupedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int GROUP_VIEW_TYPE = 0;
        private static final int CHILD_VIEW_TYPE = 1;
        private Context mContext;
        private List<Integer> mListIds;
        private Map<Integer, List<Triple<Integer, Integer, String>>> mData;
        private SparseBooleanArray mExpandedList;

        public GroupedAdapter(Context context, List<Integer> listIds, Map<Integer, List<Triple<Integer, Integer, String>>> data) {
            this.mContext = context;
            this.mListIds = listIds;
            this.mData = data;
            this.mExpandedList = new SparseBooleanArray();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == GROUP_VIEW_TYPE) {
                View view = LayoutInflater.from(mContext).inflate(R.layout.group_item_layout, parent, false);
                return new GroupViewHolder(view);
            } else {
                View view = LayoutInflater.from(mContext).inflate(R.layout.child_item_layout, parent, false);
                return new ChildViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof GroupViewHolder) {
                ((GroupViewHolder) holder).bind(position);
            } else if (holder instanceof ChildViewHolder) {
                ((ChildViewHolder) holder).bind(position);
            }
        }

        @Override
        public int getItemCount() {
            return mListIds.size() + getTotalChildItems();
        }

        @Override
        public int getItemViewType(int position) {
            if (isGroupPosition(position)) {
                return GROUP_VIEW_TYPE;
            } else {
                return CHILD_VIEW_TYPE;
            }
        }

        private boolean isGroupPosition(int position) {
            return position < mListIds.size();
        }

        private int getTotalChildItems() {
            int count = 0;
            for (int i = 0; i < mListIds.size(); i++) {
                if (mExpandedList.get(i, false)) {
                    count += mData.get(mListIds.get(i)).size();
                }
            }
            return count;
        }

        public class GroupViewHolder extends RecyclerView.ViewHolder {
            private TextView textListId;
            private ImageView expandCollapseIcon;

            public GroupViewHolder(@NonNull View itemView) {
                super(itemView);
                textListId = itemView.findViewById(R.id.text_list_id);
                expandCollapseIcon = itemView.findViewById(R.id.expand_collapse_icon);
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int position = getAdapterPosition();
                        if (mExpandedList.get(position, false)) {
                            mExpandedList.put(position, false);
                            itemView.setBackgroundColor(Color.parseColor("#FAFAFA"));
                        } else {
                            mExpandedList.put(position, true);
                            itemView.setBackgroundColor(Color.parseColor("#cefad0"));
                        }
                        notifyDataSetChanged();
                    }
                });
            }

            public void bind(int position) {
                int listId = mListIds.get(position);
                textListId.setText("List ID: " + listId);
                if (mExpandedList.get(position, false)) {
                    expandCollapseIcon.setImageResource(R.drawable.ic_expand_less);
                } else {
                    expandCollapseIcon.setImageResource(R.drawable.ic_expand_more);
                }
            }
        }

        public class ChildViewHolder extends RecyclerView.ViewHolder {
            private TextView textId;
            private TextView textName;

            public ChildViewHolder(@NonNull View itemView) {
                super(itemView);
                textId = itemView.findViewById(R.id.text_id);
                textName = itemView.findViewById(R.id.text_name);
            }

            public void bind(int position) {
                int listPosition = findListPosition(position);
                int childPosition = position - listPosition - 1;
                Triple<Integer, Integer, String> item = mData.get(mListIds.get(listPosition)).get(childPosition);
                textId.setText(" " + item.first);
                textName.setText(" " + item.third);
            }

            private int findListPosition(int position) {
                int count = 0;
                for (int i = 0; i < mListIds.size(); i++) {
                    count++; // Account for group item
                    if (mExpandedList.get(i, false)) {
                        int childCount = mData.get(mListIds.get(i)).size();
                        count += childCount;
                        if (count > position) {
                            return i;
                        }
                    }
                }
                return -1;
            }
        }
    }


}
