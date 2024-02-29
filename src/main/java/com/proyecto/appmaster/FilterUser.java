package com.proyecto.appmaster;

import android.widget.Filter;

import java.util.ArrayList;

public class FilterUser extends Filter {

    ArrayList<UserModel> filterList;

    AdapterUser adapterUser;

    public FilterUser(ArrayList<UserModel> filterList, AdapterUser adapterUser) {
        this.filterList = filterList;
        this.adapterUser = adapterUser;
    }

    protected FilterResults performFiltering(CharSequence charSequence){
        FilterResults results = new FilterResults();
        //value should not be null
        if(charSequence != null && charSequence.length() > 0){
            // change to upper case, or lower case to avoid case sensitivity
            charSequence = charSequence.toString().toUpperCase();
            ArrayList<UserModel> filteredModels = new ArrayList<>();

            for(int i=0;i<filterList.size();i++){

                //validate data
                if (filterList.get(i).getName().toUpperCase().contains(charSequence)){
                    //add to filteredMOdels
                    filteredModels.add(filterList.get(i));
                }
            }

            results.count = filteredModels.size();
            results.values = filteredModels;
        }
        else {
            results.count = filterList.size();
            results.values = filterList;
        }
        return results; //dont miss it
    }

    @Override
    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
        //apply filter changes

        adapterUser.userArrayList = (ArrayList<UserModel>) filterResults.values;

        //notify changes
        adapterUser.notifyDataSetChanged();
    }
}
