package edu.ius.rwisman.reactiontime;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

class CustomAdapter extends ArrayAdapter<String>
{
    Context context;
    List title;

    CustomAdapter(Context c, List title) {
        super(c, R.layout.listviewitem,title);
        this.context = c;
        this.title=title;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater vi = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View row = vi.inflate(R.layout.listviewitem, parent, false);
        TextView textView = (TextView) row.findViewById(R.id.textView);
        int pos = position+1;
        textView.setText((String)title.get(position));
        pos++;
        return row;
    }
}