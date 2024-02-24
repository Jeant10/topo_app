package com.proyecto.appmaster;

import android.widget.Filter;

import java.util.ArrayList;

public class FilterCategory extends Filter {
    //arraylist

    ArrayList<ModelCategory> filterList;

    //Adapter ub which filter need top be
    AdapterCategory adapterCategory;

    //contructor

    public FilterCategory(ArrayList<ModelCategory> filterList, AdapterCategory adapterCategory) {
        this.filterList = filterList;
        this.adapterCategory = adapterCategory;
    }


    @Override
    protected FilterResults performFiltering(CharSequence charSequence) {
        FilterResults results = new FilterResults();
        //value should not be null
        if(charSequence != null && charSequence.length() > 0){
            // change to upper case, or lower case to avoid case sensitivity
            charSequence = charSequence.toString().toUpperCase();
            ArrayList<ModelCategory> filteredModels = new ArrayList<>();

            for(int i=0;i<filterList.size();i++){

                //validate data
                if (filterList.get(i).getCategory().toUpperCase().contains(charSequence)){
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

        adapterCategory.categoryArrayList = (ArrayList<ModelCategory>) filterResults.values;

        //notify changes
        adapterCategory.notifyDataSetChanged();
    }
}
