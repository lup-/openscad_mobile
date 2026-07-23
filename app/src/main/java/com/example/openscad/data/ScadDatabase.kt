package com.example.openscad.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.openscad.model.ScadProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [ScadProject::class], version = 1, exportSchema = false)
abstract class ScadDatabase : RoomDatabase() {

    abstract fun scadDao(): ScadDao

    companion object {
        @Volatile
        private var INSTANCE: ScadDatabase? = null

        fun getInstance(context: Context): ScadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScadDatabase::class.java,
                    "scad_database.db"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            populateDefaults(getInstance(context).scadDao())
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateDefaults(dao: ScadDao) {
            val presets = listOf(
                ScadProject(
                    title = "Calibration Cube 20mm",
                    category = "Calibration",
                    isPreset = true,
                    description = "Standard 20mm calibration cube with hollow sphere and axis cutouts",
                    code = """
// 20mm Calibration Cube with Hollow Center
difference() {
    cube([20, 20, 20], center=true);
    
    // Inner hollow sphere
    sphere(r=8, ${"$"}fn=32);
    
    // Axis cross holes
    cylinder(h=25, r=4, center=true, ${"$"}fn=32);
    rotate([90, 0, 0]) cylinder(h=25, r=4, center=true, ${"$"}fn=32);
    rotate([0, 90, 0]) cylinder(h=25, r=4, center=true, ${"$"}fn=32);
}
                    """.trimIndent()
                ),
                ScadProject(
                    title = "Parametric Hex Bolt",
                    category = "Hardware",
                    isPreset = true,
                    description = "Hexagonal head bolt with parametric shaft and head height",
                    code = """
// Parametric Hex Bolt
module hex_bolt(head_h=6, head_r=8, shaft_h=20, shaft_r=4) {
    union() {
        // Hexagonal head
        color("orange")
            cylinder(h=head_h, r=head_r, ${"$"}fn=6);
            
        // Shaft
        color("gray")
            translate([0, 0, head_h])
                cylinder(h=shaft_h, r=shaft_r, ${"$"}fn=32);
    }
}

hex_bolt();
                    """.trimIndent()
                ),
                ScadProject(
                    title = "Parametric Spur Gear",
                    category = "Mechanical",
                    isPreset = true,
                    description = "Gear wheel with customizable teeth count and center hole",
                    code = """
// Parametric Spur Gear
module spur_gear(teeth=12, radius=14, height=5, hole_r=3) {
    difference() {
        union() {
            // Main gear body
            color("blue")
                cylinder(h=height, r=radius, ${"$"}fn=32);
                
            // Teeth array
            for (i = [0 : 360/teeth : 360]) {
                rotate([0, 0, i])
                    translate([radius, 0, height/2])
                        cube([3, 3, height], center=true);
            }
        }
        // Center shaft hole
        cylinder(h=height+2, r=hole_r, center=true, ${"$"}fn=32);
    }
}

spur_gear();
                    """.trimIndent()
                ),
                ScadProject(
                    title = "Twisted Spiral Vase",
                    category = "Vases",
                    isPreset = true,
                    description = "Modern twisted geometric vase using linear extrusion",
                    code = """
// Spiral Polygon Vase
color("yellow")
    linear_extrude(height=35, twist=72, scale=0.6) {
        circle(r=16, ${"$"}fn=6);
    }
                    """.trimIndent()
                ),
                ScadProject(
                    title = "Corner L-Bracket",
                    category = "Hardware",
                    isPreset = true,
                    description = "Mounting bracket with countersink screw holes",
                    code = """
// Corner L-Bracket
difference() {
    color("red")
        union() {
            cube([25, 8, 25]);
            cube([8, 25, 25]);
        }
        
    // Screw hole 1
    translate([16, 4, 12.5])
        rotate([90, 0, 0])
            cylinder(h=10, r=2.5, center=true, ${"$"}fn=24);
            
    // Screw hole 2
    translate([4, 16, 12.5])
        rotate([0, 90, 0])
            cylinder(h=10, r=2.5, center=true, ${"$"}fn=24);
}
                    """.trimIndent()
                )
            )

            for (p in presets) {
                dao.insertProject(p)
            }
        }
    }
}
