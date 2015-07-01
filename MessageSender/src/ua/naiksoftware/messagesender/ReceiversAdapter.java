package ua.naiksoftware.messagesender;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.List;

/**
 *
 * @author Naik
 */
public class ReceiversAdapter extends ArrayAdapter<MainActivity.Receiver> {

    public ReceiversAdapter(Context context, int resource, List<MainActivity.Receiver> objects) {
        super(context, resource, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_row, null);
            viewHolder = new ViewHolder();
            viewHolder.ip = (TextView) convertView.findViewById(R.id.row_ip);
            viewHolder.name = (TextView) convertView.findViewById(R.id.row_name);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        viewHolder.ip.setText(getItem(position).ip);
        viewHolder.name.setText(getItem(position).name);
        return convertView;
    }

    private static class ViewHolder {

        TextView ip, name;
    }

}
