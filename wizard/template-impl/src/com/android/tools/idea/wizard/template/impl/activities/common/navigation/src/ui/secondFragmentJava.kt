/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.wizard.template.impl.activities.common.navigation.src.ui

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun secondFragmentJava(
  packageName: String,
  firstFragmentClass: String,
  secondFragmentClass: String,
  secondFragmentLayoutName: String,
  navFragmentPrefix: String,
  useAndroidX: Boolean
) = """
package ${packageName}.ui.${navFragmentPrefix};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ${getMaterialComponentName("android.support.annotation.NonNull", useAndroidX)};
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)};
import androidx.navigation.fragment.NavHostFragment;

import ${packageName}.R;

public class ${secondFragmentClass} extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.${secondFragmentLayoutName}, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String myArg = ${secondFragmentClass}Args.fromBundle(getArguments()).getMyArg();
        TextView textView = view.findViewById(R.id.textview_${navFragmentPrefix}_second);
        textView.setText(getString(R.string.hello_${navFragmentPrefix}_second, myArg));

        view.findViewById(R.id.button_${navFragmentPrefix}_second).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(${secondFragmentClass}.this)
                        .navigate(R.id.action_${secondFragmentClass}_to_${firstFragmentClass});
            }
        });
    }
}
"""